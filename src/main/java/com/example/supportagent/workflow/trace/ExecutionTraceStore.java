package com.example.supportagent.workflow.trace;

import com.example.supportagent.workflow.ExecutionPlan;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo 使用的内存执行追踪仓库。
 * 所有变更都在单个 MutableTrace 上同步，避免 Graph 节点线程和 HTTP 查询线程看到半成品记录。
 */
@Component
public class ExecutionTraceStore {
    private final Map<String, MutableTrace> traces = new ConcurrentHashMap<>();

    /** 在 Graph 首次执行之前登记动态计划和可视化图。 */
    public void create(String executionId, ExecutionPlan plan) {
        traces.put(executionId, new MutableTrace(executionId, plan, toMermaid(plan)));
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
            String shape = node.approvalRequired()
                    ? "%s{{\"%s\\n[HITL]\"}}".formatted(node.id(), node.capability())
                    : "%s[\"%s\"]".formatted(node.id(), node.capability());
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

        private MutableTrace(String executionId, ExecutionPlan plan, String mermaid) {
            this.executionId = executionId;
            this.plan = plan;
            this.mermaid = mermaid;
            plan.nodes().forEach(node -> nodes.put(node.id(), new MutableNodeTrace(node)));
        }

        synchronized void nodeStarted(ExecutionPlan.PlanNode node, List<String> stateKeys) {
            currentNode = node.id();
            status = "RUNNING";
            nodes.get(node.id()).start(stateKeys);
            updatedAt = Instant.now();
        }

        synchronized void nodeCompleted(String nodeId, Map<String, Object> output) {
            nodes.get(nodeId).complete(output);
            updatedAt = Instant.now();
        }

        synchronized void nodeFailed(String nodeId, Throwable error) {
            nodes.get(nodeId).fail(error);
            status = "FAILED";
            currentNode = nodeId;
            updatedAt = Instant.now();
        }

        synchronized void status(String newStatus, String node) {
            status = newStatus;
            currentNode = node;
            updatedAt = Instant.now();
        }

        synchronized ExecutionTraceSnapshot snapshot() {
            var nodeSnapshots = nodes.values().stream().map(MutableNodeTrace::snapshot).toList();
            return new ExecutionTraceSnapshot(executionId, plan.goal(), plan.ontologyVersion(), status,
                    currentNode, startedAt, updatedAt, Duration.between(startedAt, updatedAt).toMillis(),
                    mermaid, plan.nodes(), nodeSnapshots);
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

        ExecutionTraceSnapshot.NodeTrace snapshot() {
            long elapsed = startedAt == null ? 0 : Duration.between(startedAt,
                    finishedAt == null ? Instant.now() : finishedAt).toMillis();
            return new ExecutionTraceSnapshot.NodeTrace(node.id(), node.capability(), node.implementation(),
                    status, startedAt, finishedAt, elapsed, stateKeysBefore, output, error);
        }
    }
}
