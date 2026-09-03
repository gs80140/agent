package com.example.supportagent.workflow.trace;

import com.example.supportagent.workflow.ExecutionPlan;
import com.example.supportagent.workflow.AgentStreamEvent;
import com.example.supportagent.workflow.BusinessRuleRejection;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Demo 使用的内存执行追踪仓库。
 * 所有变更都在单个 MutableTrace 上同步，避免 Graph 节点线程和 HTTP 查询线程看到半成品记录。
 */
@Component
public class ExecutionTraceStore {
    private final Map<String, MutableTrace> traces = new ConcurrentHashMap<>();

    /** 在 Graph 首次执行之前登记动态计划和可视化图。 */
    public void create(String executionId, ExecutionPlan plan) {
        create(executionId, plan, ignored -> { });
    }

    /** 创建轨迹时绑定本次 HTTP SSE 观察者。 */
    public void create(String executionId, ExecutionPlan plan, Consumer<AgentStreamEvent> events) {
        traces.put(executionId, new MutableTrace(executionId, plan, toMermaid(plan), events));
    }

    /** HITL 恢复使用的是新 HTTP 请求，因此需把观察者切换到新的 SSE 连接。 */
    public void subscribe(String executionId, Consumer<AgentStreamEvent> events) {
        trace(executionId).subscribe(events);
    }

    /** 标记节点开始，并记录进入节点前 Graph state 中已有的字段。 */
    public void nodeStarted(String executionId, ExecutionPlan.PlanNode node, List<String> stateKeys) {
        trace(executionId).nodeStarted(node, stateKeys);
    }

    /** 标记节点成功，并保存本节点写回 Graph state 的增量。 */
    public void nodeCompleted(String executionId, String nodeId, Map<String, Object> output) {
        trace(executionId).nodeCompleted(nodeId, output);
    }

    /** 标记节点失败；异常会继续抛给 Graph，此处只负责留存诊断信息。 */
    public void nodeFailed(String executionId, String nodeId, Throwable error) {
        trace(executionId).nodeFailed(nodeId, error);
    }

    /** 记录正常的业务拒绝结果；它终止流程，但不应污染系统失败指标。 */
    public void nodeRejected(String executionId, String nodeId, BusinessRuleRejection rejection) {
        trace(executionId).nodeRejected(nodeId, rejection);
    }

    public void status(String executionId, String status, String currentNode) {
        trace(executionId).status(status, currentNode);
    }

    public ExecutionTraceSnapshot get(String executionId) {
        return trace(executionId).snapshot();
    }

    private MutableTrace trace(String executionId) {
        var trace = traces.get(executionId);
        if (trace == null) throw new IllegalArgumentException("执行轨迹不存在或已过期: " + executionId);
        return trace;
    }

    /** 把本次实际生成的计划转成 Mermaid，而不是绘制一张写死的示意图。 */
    private String toMermaid(ExecutionPlan plan) {
        var graph = new StringBuilder("flowchart LR\n    START((START))");
        String previous = "START";
        for (var node : plan.nodes()) {
            String shape = node.interaction() != null
                    ? "%s{{\"%s\\n[HITL:%s]\"}}".formatted(node.id(), node.capabilityName(),
                            node.interaction().type())
                    : "%s[\"%s\"]".formatted(node.id(), node.capabilityName());
            graph.append("\n    ").append(shape);
            graph.append("\n    ").append(previous).append(" --> ").append(node.id());
            previous = node.id();
        }
        graph.append("\n    END((END))\n    ").append(previous).append(" --> END");
        return graph.toString();
    }

    private static final class MutableTrace {
        private final String executionId;
        private final ExecutionPlan plan;
        private final String mermaid;
        private final Instant startedAt = Instant.now();
        private final Map<String, MutableNodeTrace> nodes = new LinkedHashMap<>();
        private String status = "CREATED";
        private String currentNode;
        private Instant updatedAt = startedAt;
        private Consumer<AgentStreamEvent> events;

        private MutableTrace(String executionId, ExecutionPlan plan, String mermaid,
                             Consumer<AgentStreamEvent> events) {
            this.executionId = executionId;
            this.plan = plan;
            this.mermaid = mermaid;
            this.events = events;
            plan.nodes().forEach(node -> nodes.put(node.id(), new MutableNodeTrace(node)));
        }

        synchronized void nodeStarted(ExecutionPlan.PlanNode node, List<String> stateKeys) {
            currentNode = node.id();
            status = "RUNNING";
            nodes.get(node.id()).start(stateKeys);
            updatedAt = Instant.now();
            emit("NODE_STARTED", "开始执行：" + node.capabilityName(), node.id());
        }

        synchronized void nodeCompleted(String nodeId, Map<String, Object> output) {
            nodes.get(nodeId).complete(output);
            updatedAt = Instant.now();
            emit("NODE_COMPLETED", "执行完成：" + nodes.get(nodeId).node.capabilityName(), output);
        }

        synchronized void nodeFailed(String nodeId, Throwable error) {
            nodes.get(nodeId).fail(error);
            status = "FAILED";
            currentNode = nodeId;
            updatedAt = Instant.now();
            emit("NODE_FAILED", "执行失败：" + nodes.get(nodeId).node.capabilityName(), error.getMessage());
        }

        synchronized void nodeRejected(String nodeId, BusinessRuleRejection rejection) {
            nodes.get(nodeId).reject(rejection);
            status = "NOT_ELIGIBLE";
            currentNode = nodeId;
            updatedAt = Instant.now();
            emit("NODE_NOT_ELIGIBLE", rejection.userMessage(), Map.of("code", rejection.code()));
        }

        synchronized void status(String newStatus, String node) {
            status = newStatus;
            currentNode = node;
            updatedAt = Instant.now();
            String message = switch (newStatus) {
                case "WAITING_APPROVAL" -> "流程已暂停，正在等待您的确认。";
                case "WAITING_INPUT" -> "流程已暂停，正在等待客户补充资料。";
                case "COMPLETED" -> "全部 Graph 节点执行完成。";
                case "REJECTED" -> "流程已按人工决定安全终止。";
                case "NOT_ELIGIBLE" -> "业务规则核验完成，当前申请不符合办理条件。";
                case "FAILED" -> "Graph 执行失败。";
                default -> "Graph 状态更新为 " + newStatus + "。";
            };
            emit("GRAPH_" + newStatus, message, node);
        }

        synchronized void subscribe(Consumer<AgentStreamEvent> newEvents) {
            this.events = newEvents;
        }

        private void emit(String phase, String message, Object data) {
            events.accept(AgentStreamEvent.progress(phase, message, executionId, data));
        }

        synchronized ExecutionTraceSnapshot snapshot() {
            var nodeSnapshots = nodes.values().stream().map(MutableNodeTrace::snapshot).toList();
            return new ExecutionTraceSnapshot(executionId, plan.goal(), plan.ontologyVersion(),
                    plan.workflowId(), plan.workflowVersion(), status,
                    currentNode, startedAt, updatedAt, Duration.between(startedAt, updatedAt).toMillis(),
                    mermaid, plan.knowledgeReferences(), plan.planningReasoning(), plan.nodes(), nodeSnapshots);
        }
    }

    private static final class MutableNodeTrace {
        private final ExecutionPlan.PlanNode node;
        private String status = "PENDING";
        private Instant startedAt;
        private Instant finishedAt;
        private List<String> stateKeysBefore = List.of();
        private Map<String, Object> output = Map.of();
        private String error;

        private MutableNodeTrace(ExecutionPlan.PlanNode node) { this.node = node; }

        void start(List<String> keys) {
            status = "RUNNING";
            startedAt = Instant.now();
            stateKeysBefore = List.copyOf(new ArrayList<>(keys));
        }

        void complete(Map<String, Object> values) {
            status = "COMPLETED";
            finishedAt = Instant.now();
            output = Map.copyOf(values);
        }

        void fail(Throwable throwable) {
            status = "FAILED";
            finishedAt = Instant.now();
            error = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }

        void reject(BusinessRuleRejection rejection) {
            status = "NOT_ELIGIBLE";
            finishedAt = Instant.now();
            error = rejection.code() + ": " + rejection.userMessage();
        }

        ExecutionTraceSnapshot.NodeTrace snapshot() {
            long elapsed = startedAt == null ? 0 : Duration.between(startedAt,
                    finishedAt == null ? Instant.now() : finishedAt).toMillis();
            return new ExecutionTraceSnapshot.NodeTrace(node.id(), node.capabilityName(), node.implementation(),
                    status, startedAt, finishedAt, elapsed, stateKeysBefore, output, error);
        }
    }
}
