package com.example.supportagent.workflow;

import com.example.supportagent.ontology.OntologyDefinition;
import com.example.supportagent.ontology.OntologyDefinition.CapabilityDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class OntologyCapabilityPlanner {

    public ExecutionPlan plan(String userPrompt, OntologyDefinition ontology) {
        var goalEntry = ontology.goals().entrySet().stream()
                .filter(entry -> matches(userPrompt, entry.getValue().triggers()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前 Ontology 无法匹配该用户目标"));

        var ordered = new LinkedHashSet<String>();
        var available = new HashSet<>(Set.of("UserRequestAvailable"));
        for (String desiredFact : goalEntry.getValue().desiredFacts()) {
            resolveFact(desiredFact, ontology, available, ordered, new HashSet<>());
        }

        var nodes = new ArrayList<ExecutionPlan.PlanNode>();
        int index = 1;
        for (String capabilityName : ordered) {
            var capability = ontology.capabilities().get(capabilityName);
            nodes.add(toNode(index++, capabilityName, capability));
        }
        return new ExecutionPlan(goalEntry.getKey(), ontology.version(), nodes);
    }

    private void resolveFact(String fact, OntologyDefinition ontology, Set<String> available,
                             LinkedHashSet<String> ordered, Set<String> resolving) {
        if (available.contains(fact)) return;
        if (!resolving.add(fact)) throw new IllegalArgumentException("Ontology 存在循环事实依赖: " + fact);
        var producer = ontology.capabilities().entrySet().stream()
                .filter(entry -> entry.getValue().produces().contains(fact))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有能力可以产出事实: " + fact));
        for (String required : producer.getValue().requires()) {
            resolveFact(required, ontology, available, ordered, resolving);
        }
        ordered.add(producer.getKey());
        available.addAll(producer.getValue().produces());
        resolving.remove(fact);
    }

    private boolean matches(String prompt, List<String> triggers) {
        var normalized = prompt.toLowerCase(Locale.ROOT);
        return triggers.stream().anyMatch(normalized::contains);
    }

    private ExecutionPlan.PlanNode toNode(int index, String name, CapabilityDefinition capability) {
        return new ExecutionPlan.PlanNode("n" + index + "_" + name, name, capability.implementation(),
                capability.effect(), capability.approvalRequired(), capability.requires(), capability.produces());
    }
}
