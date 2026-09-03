package com.example.supportagent.workflow;

import com.example.supportagent.ontology.OntologyDefinition;
import com.example.supportagent.ontology.ResolvedIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

/** 根据本体意图确定性选择并实例化企业已发布流程；这里没有任何 LLM 调用。 */
@Component
public class WorkflowResolver {
    private final WorkflowRepository workflows;
    private final CapabilityCatalog capabilities;

    public WorkflowResolver(WorkflowRepository workflows, CapabilityCatalog capabilities) {
        this.workflows = workflows;
        this.capabilities = capabilities;
    }

    public ExecutionPlan resolve(ResolvedIntent intent, OntologyDefinition ontology) {
        WorkflowDefinition workflow = workflows.requirePublishedForIntent(intent.intentId());
        validateOntologyBinding(workflow, intent, ontology);
        validateAxioms(workflow, ontology);
        var nodes = new ArrayList<ExecutionPlan.PlanNode>();
        for (var node : workflow.nodes()) {
            CapabilitySchema schema = capabilities.require(node.capabilityId()).schema();
            nodes.add(new ExecutionPlan.PlanNode(node.id(), schema.id(), schema.name(), schema.implementation(),
                    schema.effect(), schema.approvalRequired(), schema.requiredInputs(), schema.outputs(),
                    node.knowledgeConceptIds(), node.interaction()));
        }
        return new ExecutionPlan(workflow.goal(), ontology.version(), workflow.id(), workflow.version(),
                java.util.List.of(), "本体意图：" + intent.intentId() + "；" + intent.reasoning(), nodes);
    }

    private void validateOntologyBinding(WorkflowDefinition workflow, ResolvedIntent intent,
                                         OntologyDefinition ontology) {
        if (workflow.status() != WorkflowDefinition.Status.PUBLISHED || workflow.nodes().isEmpty()) {
            throw new IllegalArgumentException("只能解析包含节点的已发布 Workflow");
        }
        var conceptIds = ontology.concepts().stream().map(OntologyDefinition.Concept::id)
                .collect(Collectors.toSet());
        if (workflow.appliesToIntents().isEmpty()
                || workflow.appliesToIntents().stream().anyMatch(id -> !conceptIds.contains(id))) {
            throw new IllegalArgumentException("Workflow 引用了不存在的 Ontology 意图: " + workflow.appliesToIntents());
        }
        var nodeIds = new HashSet<String>();
        workflow.nodes().forEach(node -> {
            if (!nodeIds.add(node.id())) throw new IllegalArgumentException("Workflow 节点 ID 重复: " + node.id());
            capabilities.require(node.capabilityId());
            node.knowledgeConceptIds().forEach(id -> {
                if (!conceptIds.contains(id)) throw new IllegalArgumentException("知识检索引用未知 Ontology 概念: " + id);
            });
            if (node.interaction() != null
                    && !node.interaction().properties().keySet().containsAll(node.interaction().required())) {
                throw new IllegalArgumentException("Workflow 交互 Schema 的 required 字段不存在: " + node.id());
            }
        });
        var instance = ontology.instances().stream()
                .filter(i -> i.id().equals(workflow.ontologyInstanceId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程未绑定 Ontology BusinessProcess 实例: " + workflow.id()));
        if (!workflow.id().equals(String.valueOf(instance.properties().get("workflowId")))
                || !workflow.version().equals(String.valueOf(instance.properties().get("workflowVersion")))
                || !"PUBLISHED".equals(String.valueOf(instance.properties().get("status")))) {
            throw new IllegalArgumentException("Workflow 与 Ontology 流程实例的 ID、版本或发布状态不一致");
        }
        // 必须是“当前识别意图”的业务目标绑定该流程，不能借用其他目标的 realized-by 关系。
        boolean realizedByGoal = ontology.instances().stream()
                .filter(i -> intent.intentId().equals(String.valueOf(i.properties().get("intentId"))))
                .anyMatch(i -> i.relations().getOrDefault("realized-by", java.util.List.of()).contains(instance.id()));
        if (!realizedByGoal) throw new IllegalArgumentException("Ontology 中没有业务目标由该流程实例实现");
    }

    private void validateAxioms(WorkflowDefinition workflow, OntologyDefinition ontology) {
        var positions = new HashMap<String, Integer>();
        for (int i = 0; i < workflow.nodes().size(); i++) positions.put(workflow.nodes().get(i).semanticType(), i);
        ontology.axioms().stream().filter(a -> a.type().equals("PROCESS_ORDER"))
                .filter(a -> a.id().equals("return-refund-required-order"))
                .forEach(axiom -> {
                    String[] ordered = axiom.expression().split("\\s*<\\s*");
                    int previous = -1;
                    for (String semanticType : ordered) {
                        Integer position = positions.get(semanticType);
                        if (position == null || position <= previous) {
                            throw new IllegalArgumentException("Workflow 违反本体流程偏序公理 " + axiom.id()
                                    + "，节点=" + semanticType);
                        }
                        previous = position;
                    }
                });
    }
}
