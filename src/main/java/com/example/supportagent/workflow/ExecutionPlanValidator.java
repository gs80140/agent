package com.example.supportagent.workflow;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 动态计划的安全门。
 *
 * <p>Ontology 可以动态变化，但只有通过节点数量、实现白名单、事实可达性和副作用审批
 * 检查的计划才允许编译执行。</p>
 */
@Component
public class ExecutionPlanValidator {
    private static final int MAX_NODES = 20;
    private final CapabilityCatalog catalog;

    public ExecutionPlanValidator(CapabilityCatalog catalog) { this.catalog = catalog; }

    /** 按执行顺序模拟事实集合的变化，并验证每个节点当时是否真的可执行。 */
    public void validate(ExecutionPlan plan) {
        if (plan.nodes().isEmpty() || plan.nodes().size() > MAX_NODES) {
            throw new IllegalArgumentException("执行计划节点数必须在 1 到 " + MAX_NODES + " 之间");
        }
        Set<String> facts = new HashSet<>(Set.of("userPrompt", "executionId"));
        boolean approvalSeen = false;
        for (var node : plan.nodes()) {
            // capabilityId 必须来自 Spring 启动时收集的受控 CapabilityHandler。
            if (!catalog.contains(node.capabilityId())) {
                throw new IllegalArgumentException("计划引用了未注册能力: " + node.capabilityId());
            }
            CapabilitySchema schema = catalog.require(node.capabilityId()).schema();
            if (!schema.implementation().equals(node.implementation())
                    || !schema.requiredInputs().equals(node.requires())
                    || !schema.outputs().equals(node.produces())
                    || schema.effect() != node.effect()
                    || schema.approvalRequired() != node.approvalRequired()) {
                throw new IllegalArgumentException("计划中的机器契约与代码 Schema 不一致: " + node.capabilityId());
            }
            if (node.interaction() != null) {
                // 交互节点在恢复前由 InteractionInputValidator 产生这些外部事实。
                facts.addAll(node.interaction().required());
                if (!node.interaction().properties().keySet().containsAll(node.interaction().required())) {
                    throw new IllegalArgumentException("人工交互 required 引用了未定义字段: " + node.id());
                }
            }
            if (!facts.containsAll(node.requires())) {
                var missing = new HashSet<>(node.requires());
                missing.removeAll(facts);
                throw new IllegalArgumentException("计划前置事实不满足，node=" + node.id() + ", missing=" + missing);
            }
            if (node.approvalRequired()) {
                if (node.interaction() == null || node.interaction().type() != HumanInteraction.Type.APPROVAL) {
                    throw new IllegalArgumentException("审批能力必须绑定 APPROVAL 交互: " + node.id());
                }
                approvalSeen = true;
            }
            // READ 不需要审批；任何 WRITE/EXTERNAL_WRITE 必须位于审批能力之后。
            if ((node.effect().name().contains("WRITE")) && !approvalSeen) {
                throw new IllegalArgumentException("有副作用的能力之前必须存在审批节点: " + node.capabilityName());
            }
            facts.addAll(node.produces());
        }
    }
}
