package com.example.supportagent.ontology;

import org.springframework.util.StringUtils;

import java.util.HashSet;

public final class OntologyValidator {
    private OntologyValidator() {}

    public static void validateDefinition(OntologyDefinition ontology) {
        if (ontology == null || !StringUtils.hasText(ontology.version())) {
            throw new IllegalArgumentException("Ontology version 不能为空");
        }
        if (ontology.goals().isEmpty() || ontology.capabilities().isEmpty()) {
            throw new IllegalArgumentException("Ontology 至少需要一个 goal 和一个 capability");
        }
        var producedFacts = new HashSet<String>();
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
