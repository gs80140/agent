package com.example.supportagent.ontology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OntologyDefinition(
        String version,
        Map<String, GoalDefinition> goals,
        Map<String, CapabilityDefinition> capabilities) {

    public OntologyDefinition {
        goals = goals == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(goals));
        capabilities = capabilities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(capabilities));
    }

    public record GoalDefinition(List<String> triggers, List<String> desiredFacts) {
        public GoalDefinition {
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
            desiredFacts = desiredFacts == null ? List.of() : List.copyOf(desiredFacts);
        }
    }

    public record CapabilityDefinition(
            String implementation,
            List<String> requires,
            List<String> produces,
            EffectLevel effect,
            boolean approvalRequired,
            String description) {
        public CapabilityDefinition {
            requires = requires == null ? List.of() : List.copyOf(requires);
            produces = produces == null ? List.of() : List.copyOf(produces);
            effect = effect == null ? EffectLevel.NONE : effect;
        }
    }

    public enum EffectLevel { NONE, READ, WRITE, EXTERNAL_WRITE }
}
