package com.example.supportagent.workflow;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class ExecutionPlanValidator {
    private static final int MAX_NODES = 20;
    private final CapabilityCatalog catalog;

    public ExecutionPlanValidator(CapabilityCatalog catalog) { this.catalog = catalog; }

    public void validate(ExecutionPlan plan) {
        if (plan.nodes().isEmpty() || plan.nodes().size() > MAX_NODES) {
            throw new IllegalArgumentException("执行计划节点数必须在 1 到 " + MAX_NODES + " 之间");
        }
        Set<String> facts = new HashSet<>(Set.of("UserRequestAvailable"));
        boolean approvalSeen = false;
        for (var node : plan.nodes()) {
            if (!catalog.contains(node.implementation())) {
                throw new IllegalArgumentException("计划引用了未注册实现: " + node.implementation());
            }
            if (!facts.containsAll(node.requires())) {
                var missing = new HashSet<>(node.requires());
                missing.removeAll(facts);
                throw new IllegalArgumentException("计划前置事实不满足，node=" + node.id() + ", missing=" + missing);
            }
            if (node.approvalRequired()) approvalSeen = true;
            if ((node.effect().name().contains("WRITE")) && !approvalSeen) {
                throw new IllegalArgumentException("有副作用的能力之前必须存在审批节点: " + node.capability());
            }
            facts.addAll(node.produces());
        }
    }
}
