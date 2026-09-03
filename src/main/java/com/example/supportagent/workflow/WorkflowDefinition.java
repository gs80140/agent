package com.example.supportagent.workflow;

import java.util.List;

/** 企业发布的可执行流程定义；它是流程本体实例对应的结构化执行投影。 */
public record WorkflowDefinition(
        String id,
        String version,
        String name,
        Status status,
        String ontologyInstanceId,
        List<String> appliesToIntents,
        String goal,
        List<WorkflowNode> nodes) {

    public WorkflowDefinition {
        appliesToIntents = appliesToIntents == null ? List.of() : List.copyOf(appliesToIntents);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public enum Status { DRAFT, PUBLISHED, RETIRED }

    /** semanticType 用于公理校验；capabilityId 仅引用 Spring 自动扫描出来的真实能力。 */
    public record WorkflowNode(String id, String semanticType, String capabilityId,
                               List<String> knowledgeConceptIds, HumanInteraction interaction) {
        public WorkflowNode {
            knowledgeConceptIds = knowledgeConceptIds == null ? List.of() : List.copyOf(knowledgeConceptIds);
        }
    }
}
