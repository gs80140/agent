package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.example.supportagent.workflow.trace.ExecutionTraceStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** 管理 Graph 首次执行、多个 HITL 中断、人工输入校验和 checkpoint 恢复。 */
@Component
public class AgentGraphRuntime {
    private final Map<String, ExecutionSession> sessions = new ConcurrentHashMap<>();
    private final ExecutionTraceStore traceStore;
    private final InteractionInputValidator inputValidator;

    public AgentGraphRuntime(ExecutionTraceStore traceStore, InteractionInputValidator inputValidator) {
        this.traceStore = traceStore;
        this.inputValidator = inputValidator;
    }

    public AgentExecutionResponse start(CompiledGraph graph, ExecutionPlan plan, String userPrompt) {
        return start(graph, plan, userPrompt, ignored -> { });
    }

    public AgentExecutionResponse start(CompiledGraph graph, ExecutionPlan plan, String userPrompt,
                                        Consumer<AgentStreamEvent> events) {
        String executionId = UUID.randomUUID().toString();
        var config = RunnableConfig.builder().threadId(executionId).build();
        traceStore.create(executionId, plan, events);
        events.accept(AgentStreamEvent.progress("GRAPH_STARTED", "Graph execution 已创建。", executionId,
                Map.of("ontologyVersion", plan.ontologyVersion(), "goal", plan.goal())));
        traceStore.status(executionId, "RUNNING", "START");
        try {
            OverAllState state = graph.invoke(Map.of("executionId", executionId, "userPrompt", userPrompt), config)
                    .orElseThrow(() -> new IllegalStateException("Graph 未返回执行状态"));
            return afterInvoke(executionId, graph, plan, config, state, 0);
        } catch (RuntimeException exception) {
            var rejection = findBusinessRejection(exception);
            if (rejection != null) return businessRejection(executionId, plan, rejection);
            traceStore.status(executionId, "FAILED", "GRAPH_RUNTIME");
            throw exception;
        }
    }

    /** 兼容原审批 API；它只允许处理当前类型为 APPROVAL 的交互。 */
    public AgentExecutionResponse decide(String executionId, boolean approved) {
        return decide(executionId, approved, ignored -> { });
    }

    public AgentExecutionResponse decide(String executionId, boolean approved, Consumer<AgentStreamEvent> events) {
        ExecutionSession session = requireSession(executionId);
        // 同一 execution 的恢复必须串行化。否则用户双击或客户端重试可能让副作用节点执行两次。
        synchronized (session) {
            ensureCurrent(session);
            if (session.pending().type() != HumanInteraction.Type.APPROVAL) {
                throw new IllegalArgumentException("当前等待的是资料补充，不是审批操作");
            }
            traceStore.subscribe(executionId, events);
            events.accept(AgentStreamEvent.progress("HITL_DECISION", approved
                    ? "已收到批准，正在从检查点恢复 Graph…" : "已收到拒绝，正在安全终止流程。", executionId, null));
            if (!approved) {
                sessions.remove(executionId, session);
                traceStore.status(executionId, "REJECTED", null);
                return response(executionId, AgentExecutionResponse.Status.REJECTED,
                        "已拒绝本次操作，未创建售后工单，也未发送通知。", session.plan(), null);
            }
            return resume(session, Map.of("approvalDecision", "APPROVE"), events);
        }
    }

    /** 提交 FORM_INPUT/FILE_UPLOAD/CLARIFICATION/SELECTION 等通用人工交互。 */
    public AgentExecutionResponse submitInteraction(String executionId, String interactionId,
                                                     Map<String, Object> values,
                                                     Consumer<AgentStreamEvent> events) {
        ExecutionSession session = requireSession(executionId);
        synchronized (session) {
            ensureCurrent(session);
            if (!session.pending().interactionId().equals(interactionId)) {
                throw new IllegalArgumentException("交互已过期或不属于当前中断节点");
            }
            if (session.pending().type() == HumanInteraction.Type.APPROVAL) {
                throw new IllegalArgumentException("审批交互必须使用 decision 接口");
            }
            if (Instant.now().isAfter(session.pending().expiresAt())) {
                sessions.remove(executionId, session);
                throw new IllegalArgumentException("补充资料交互已过期，请重新发起流程");
            }
            traceStore.subscribe(executionId, events);
            Map<String, Object> normalized = inputValidator.validate(session.pending(), values);
            events.accept(AgentStreamEvent.progress("HITL_INPUT_RECEIVED", "补充资料校验通过，正在恢复 Graph…",
                    executionId, Map.of("fields", normalized.keySet())));
            return resume(session, normalized, events);
        }
    }

    private AgentExecutionResponse resume(ExecutionSession session, Map<String, Object> input,
                                          Consumer<AgentStreamEvent> events) {
        try {
            RunnableConfig updated = session.graph().updateState(session.config(), input);
            OverAllState state = session.graph().invoke(Map.of(), updated.withResume())
                    .orElseThrow(() -> new IllegalStateException("Graph 恢复后未返回状态"));
            // 不携带旧 checkpointId，让 Saver 在下一次人工输入时按 threadId 取得最新 checkpoint。
            RunnableConfig latest = RunnableConfig.builder().threadId(session.executionId()).build();
            return afterInvoke(session.executionId(), session.graph(), session.plan(), latest, state,
                    session.interactionIndex() + 1);
        } catch (Exception exception) {
            var rejection = findBusinessRejection(exception);
            if (rejection != null) {
                sessions.remove(session.executionId(), session);
                return businessRejection(session.executionId(), session.plan(), rejection);
            }
            traceStore.status(session.executionId(), "FAILED", "GRAPH_RUNTIME");
            throw new IllegalStateException("Graph 人工交互恢复执行失败", exception);
        }
    }

    /** 把预期的规则拒绝转换为可展示的业务终态，技术异常仍沿原路径上抛。 */
    private AgentExecutionResponse businessRejection(String executionId, ExecutionPlan plan,
                                                     BusinessRuleRejection rejection) {
        // 编译器代理已经在实际判定节点记录 NOT_ELIGIBLE，保留该节点作为 currentNode 方便排障。
        return response(executionId, AgentExecutionResponse.Status.NOT_ELIGIBLE,
                rejection.userMessage(), plan, null);
    }

    /** Graph/异步执行器可能包裹原异常，因此沿 cause 链寻找领域结果。 */
    private BusinessRuleRejection findBusinessRejection(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof BusinessRuleRejection rejection) return rejection;
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return null;
    }

    private AgentExecutionResponse afterInvoke(String executionId, CompiledGraph graph, ExecutionPlan plan,
                                               RunnableConfig config, OverAllState state, int interactionIndex) {
        List<ExecutionPlan.PlanNode> interactionNodes = plan.nodes().stream()
                .filter(node -> node.interaction() != null).toList();
        if (interactionIndex >= interactionNodes.size()) {
            sessions.remove(executionId);
            traceStore.status(executionId, "COMPLETED", "END");
            return response(executionId, AgentExecutionResponse.Status.COMPLETED,
                    value(state, "finalResponse", "流程执行完成。"), plan, null);
        }

        var node = interactionNodes.get(interactionIndex);
        var definition = node.interaction();
        var pending = new PendingInteraction(UUID.randomUUID().toString(), node.id(), definition.type(),
                definition.title(), definition.description(), definition.required(), definition.properties(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        sessions.put(executionId, new ExecutionSession(executionId, graph, plan, config, interactionIndex, pending));
        AgentExecutionResponse.Status status = definition.type() == HumanInteraction.Type.APPROVAL
                ? AgentExecutionResponse.Status.WAITING_APPROVAL : AgentExecutionResponse.Status.WAITING_INPUT;
        traceStore.status(executionId, status.name(), node.id());
        String content = definition.type() == HumanInteraction.Type.APPROVAL
                ? "已完成资料和售后政策核验。即将为 **%s**（订单号：`%s`）执行 `%s`，是否批准？"
                    .formatted(value(state, "productName", "目标商品"), value(state, "orderId", "未知"),
                            value(state, "serviceType", "REFUND"))
                : definition.description();
        return response(executionId, status, content, plan, pending);
    }

    private ExecutionSession requireSession(String executionId) {
        var session = sessions.get(executionId);
        if (session == null) throw new IllegalArgumentException("执行不存在、已完成或已过期: " + executionId);
        return session;
    }

    /** 锁内再次确认 session 仍是当前版本，拒绝重复提交和旧页面重放。 */
    private void ensureCurrent(ExecutionSession session) {
        if (sessions.get(session.executionId()) != session) {
            throw new IllegalArgumentException("交互已处理，请刷新执行状态");
        }
    }

    private AgentExecutionResponse response(String id, AgentExecutionResponse.Status status, String content,
                                            ExecutionPlan plan, PendingInteraction interaction) {
        List<String> capabilities = plan.nodes().stream().map(ExecutionPlan.PlanNode::capabilityName).toList();
        return new AgentExecutionResponse(id, status, content, plan.ontologyVersion(), plan.goal(),
                capabilities, interaction);
    }

    private String value(OverAllState state, String key, String fallback) {
        return state.value(key).map(String::valueOf).orElse(fallback);
    }

    private record ExecutionSession(String executionId, CompiledGraph graph, ExecutionPlan plan,
                                    RunnableConfig config, int interactionIndex, PendingInteraction pending) {}
}
