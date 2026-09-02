package com.example.supportagent.ontology;

import org.springframework.util.StringUtils;

import java.util.HashSet;

/** 发布 Ontology 前执行的结构性校验，防止无解知识进入运行时。 */
public final class OntologyValidator {
    private OntologyValidator() {}

    /**
     * 校验版本、目标和能力的基本完整性。
     * 这里验证的是知识定义本身；单次执行计划还会由 ExecutionPlanValidator 再次校验。
     */
    public static void validateDefinition(OntologyDefinition ontology) {
        if (ontology == null || !StringUtils.hasText(ontology.version())) {
            throw new IllegalArgumentException("Ontology version 不能为空");
        }
        if (ontology.goals().isEmpty() || ontology.capabilities().isEmpty()) {
            throw new IllegalArgumentException("Ontology 至少需要一个 goal 和一个 capability");
        }
        var producedFacts = new HashSet<String>();
        // 汇总所有可被能力产生的事实，用来检查目标是否至少存在潜在生产者。
        ontology.capabilities().forEach((name, capability) -> {
            if (!StringUtils.hasText(name) || !StringUtils.hasText(capability.implementation())) {
                throw new IllegalArgumentException("Capability 名称和 implementation 不能为空");
            }
            producedFacts.addAll(capability.produces());
        });
        ontology.goals().forEach((name, goal) -> {
            if (goal.desiredFacts().isEmpty() || !producedFacts.containsAll(goal.desiredFacts())) {
                throw new IllegalArgumentException("Goal 无法由已声明能力满足: " + name);
            }
        });
    }
}
