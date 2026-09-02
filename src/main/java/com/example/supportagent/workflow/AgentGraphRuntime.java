package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.example.supportagent.workflow.trace.ExecutionTraceStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph execution 的生命周期管理器，负责首次运行、中断登记、人工决策和 checkpoint 恢复。
 *
 * <p>Demo 使用 ConcurrentHashMap + MemorySaver，因此应用重启后等待审批的 execution 会丢失；
 * 生产环境应把会话索引和 checkpoint 同时替换为 Redis/JDBC 等持久化实现。</p>
 */
@Component
public class AgentGraphRuntime {
    /** 只保存仍在等待人工决策的 execution；完成或拒绝后立即移除。 */
    private final Map<String, ExecutionSession> sessions = new ConcurrentHashMap<>();
    private final ExecutionTraceStore traceStore;

    public AgentGraphRuntime(ExecutionTraceStore traceStore) {
        this.traceStore = traceStore;
    }

    /** 启动 Graph。包含审批节点的计划会运行到第一个 interruptBefore 为止。 */
    public AgentExecutionResponse start(CompiledGraph graph, ExecutionPlan plan, String userPrompt) {
        String executionId = UUID.randomUUID().toString();
        var config = RunnableConfig.builder().threadId(executionId).build();
        traceStore.create(executionId, plan);
        traceStore.status(executionId, "RUNNING", StateGraphNode.START);
        OverAllState state;
        try {
            state = graph.invoke(Map.of("executionId", executionId, "userPrompt", userPrompt), config)
                    .orElseThrow(() -> new IllegalStateException("Graph 未返回执行状态"));
        } catch (RuntimeException exception) {
            traceStore.status(executionId, "FAILED", "GRAPH_RUNTIME");
            throw exception;
        }
        boolean requiresApproval = plan.nodes().stream().anyMatch(ExecutionPlan.PlanNode::approvalRequired);
        if (!requiresApproval) return completed(executionId, plan, state);

        // 保存原始 config 很重要：它包含 threadId，MemorySaver 以此定位对应 checkpoint。
        sessions.put(executionId, new ExecutionSession(graph, plan, config));
        String approvalNode = plan.nodes().stream().filter(ExecutionPlan.PlanNode::approvalRequired)
                .findFirst().map(ExecutionPlan.PlanNode::id).orElse("HUMAN_APPROVAL");
        traceStore.status(executionId, "WAITING_APPROVAL", approvalNode);
        String content = "已完成订单与售后政策核验。即将为 **%s**（订单号：`%s`）执行 `%s`，是否批准？"
                .formatted(value(state, "productName", "目标商品"), value(state, "orderId", "未知"),
                        value(state, "serviceType", "REFUND"));
        return response(executionId, AgentExecutionResponse.Status.WAITING_APPROVAL, content, plan);
    }

    /**
     * 处理人工决策。拒绝时不恢复 Graph；批准时先把决策写入 checkpoint，再从中断节点恢复。
     */
    public AgentExecutionResponse decide(String executionId, boolean approved) {
        var session = sessions.get(executionId);
        if (session == null) throw new IllegalArgumentException("执行不存在、已完成或已过期: " + executionId);
        if (!approved) {
            // Graph 尚未越过审批节点，因此直接结束即可保证所有写能力都没有执行。
            sessions.remove(executionId);
            traceStore.status(executionId, "REJECTED", null);
            return response(executionId, AgentExecutionResponse.Status.REJECTED,
                    "已拒绝本次操作，未创建售后工单，也未发送通知。", session.plan());
        }
        try {
            // updateState 返回带 checkpointId 的新配置，不能丢弃后重新构造 RunnableConfig。
            RunnableConfig updated = session.graph().updateState(session.config(),
                    Map.of("approvalDecision", "APPROVE"));
            // withResume 告诉 Graph 跳过已触发过的 interruptBefore，从暂停节点继续。
            OverAllState state = session.graph().invoke(Map.of(), updated.withResume())
                    .orElseThrow(() -> new IllegalStateException("Graph 恢复后未返回状态"));
            sessions.remove(executionId);
            return completed(executionId, session.plan(), state);
        } catch (Exception exception) {
            traceStore.status(executionId, "FAILED", "GRAPH_RUNTIME");
            throw new IllegalStateException("Graph 审批恢复执行失败", exception);
        }
    }

    private AgentExecutionResponse completed(String id, ExecutionPlan plan, OverAllState state) {
        traceStore.status(id, "COMPLETED", StateGraphNode.END);
        return response(id, AgentExecutionResponse.Status.COMPLETED,
                value(state, "finalResponse", "流程执行完成。"), plan);
    }

    private AgentExecutionResponse response(String id, AgentExecutionResponse.Status status,
                                            String content, ExecutionPlan plan) {
        List<String> capabilities = plan.nodes().stream().map(ExecutionPlan.PlanNode::capability).toList();
        return new AgentExecutionResponse(id, status, content, plan.ontologyVersion(), plan.goal(), capabilities);
    }

    private String value(OverAllState state, String key, String fallback) {
        return state.value(key).map(String::valueOf).orElse(fallback);
    }

    /** 仅存在于本进程中的等待审批会话。 */
    private record ExecutionSession(CompiledGraph graph, ExecutionPlan plan, RunnableConfig config) {}

    /** 避免运行时层直接依赖 StateGraph 的特殊节点常量。 */
    private static final class StateGraphNode {
        private static final String START = "START";
        private static final String END = "END";
        private StateGraphNode() {}
    }
}
