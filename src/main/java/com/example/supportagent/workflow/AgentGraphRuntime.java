package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentGraphRuntime {
    private final Map<String, ExecutionSession> sessions = new ConcurrentHashMap<>();

    public AgentExecutionResponse start(CompiledGraph graph, ExecutionPlan plan, String userPrompt) {
        String executionId = UUID.randomUUID().toString();
        var config = RunnableConfig.builder().threadId(executionId).build();
        var state = graph.invoke(Map.of("userPrompt", userPrompt), config)
                .orElseThrow(() -> new IllegalStateException("Graph 未返回执行状态"));
        boolean requiresApproval = plan.nodes().stream().anyMatch(ExecutionPlan.PlanNode::approvalRequired);
        if (!requiresApproval) return completed(executionId, plan, state);

        sessions.put(executionId, new ExecutionSession(graph, plan, config));
        String content = "已完成订单与售后政策核验。即将为 **%s**（订单号：`%s`）执行 `%s`，是否批准？"
                .formatted(value(state, "productName", "目标商品"), value(state, "orderId", "未知"),
                        value(state, "serviceType", "REFUND"));
        return response(executionId, AgentExecutionResponse.Status.WAITING_APPROVAL, content, plan);
    }

    public AgentExecutionResponse decide(String executionId, boolean approved) {
        var session = sessions.get(executionId);
        if (session == null) throw new IllegalArgumentException("执行不存在、已完成或已过期: " + executionId);
        if (!approved) {
            sessions.remove(executionId);
            return response(executionId, AgentExecutionResponse.Status.REJECTED,
                    "已拒绝本次操作，未创建售后工单，也未发送通知。", session.plan());
        }
        try {
            RunnableConfig updated = session.graph().updateState(session.config(),
                    Map.of("approvalDecision", "APPROVE"));
            OverAllState state = session.graph().invoke(Map.of(), updated.withResume())
                    .orElseThrow(() -> new IllegalStateException("Graph 恢复后未返回状态"));
            sessions.remove(executionId);
            return completed(executionId, session.plan(), state);
        } catch (Exception exception) {
            throw new IllegalStateException("Graph 审批恢复执行失败", exception);
        }
    }

    private AgentExecutionResponse completed(String id, ExecutionPlan plan, OverAllState state) {
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

    private record ExecutionSession(CompiledGraph graph, ExecutionPlan plan, RunnableConfig config) {}
}
