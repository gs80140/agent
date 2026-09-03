package com.example.supportagent.workflow;

import com.example.supportagent.workflow.CapabilitySchema.EffectLevel;

import java.util.List;

/**
 * Planner 输出、Graph Compiler 输入的结构化执行计划。
 * 它固定本次执行使用的 Ontology 版本，不能在暂停恢复期间切换版本。
 */
public record ExecutionPlan(
        String goal,
        String ontologyVersion,
        String workflowId,
        String workflowVersion,
        List<String> knowledgeReferences,
        String planningReasoning,
        List<PlanNode> nodes) {

    public ExecutionPlan {
        knowledgeReferences = List.copyOf(knowledgeReferences);
        nodes = List.copyOf(nodes);
    }

    /** 单个计划节点，保留语义信息以便编译前做权限和副作用校验。 */
    public record PlanNode(
            String id,
            String capabilityId,
            String capabilityName,
            String implementation,
            EffectLevel effect,
            boolean approvalRequired,
            List<String> requires,
            List<String> produces,
            List<String> knowledgeConceptIds,
            HumanInteraction interaction) {
        public PlanNode {
            requires = List.copyOf(requires);
            produces = List.copyOf(produces);
            knowledgeConceptIds = knowledgeConceptIds == null ? List.of() : List.copyOf(knowledgeConceptIds);
        }
    }
}
