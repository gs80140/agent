package com.example.supportagent.workflow.trace;

import com.example.supportagent.workflow.ExecutionPlan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 一次 Graph execution 的只读诊断快照，可直接通过 REST 序列化给调试页面。
 * 快照只记录节点可见状态键和节点输出，不记录 API Key、Nacos 凭证等应用配置。
 */
public record ExecutionTraceSnapshot(
        String executionId,
        String goal,
        String ontologyVersion,
        String status,
        String currentNode,
        Instant startedAt,
        Instant updatedAt,
        long elapsedMs,
        String mermaid,
        List<ExecutionPlan.PlanNode> plan,
        List<NodeTrace> nodes) {

    /** 单节点的一次执行记录。Graph 当前为无环计划，因此一个节点对应一条记录。 */
    public record NodeTrace(
            String nodeId,
            String capability,
            String implementation,
            String status,
            Instant startedAt,
            Instant finishedAt,
            long elapsedMs,
            List<String> stateKeysBefore,
            Map<String, Object> output,
            String error) {}
}
