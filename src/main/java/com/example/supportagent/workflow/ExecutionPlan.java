package com.example.supportagent.workflow;

import com.example.supportagent.ontology.OntologyDefinition.EffectLevel;

import java.util.List;

public record ExecutionPlan(
        String goal,
        String ontologyVersion,
        List<PlanNode> nodes) {

    public ExecutionPlan {
        nodes = List.copyOf(nodes);
    }

    public record PlanNode(
            String id,
            String capability,
            String implementation,
            EffectLevel effect,
            boolean approvalRequired,
            List<String> requires,
            List<String> produces) {
        public PlanNode {
            requires = List.copyOf(requires);
            produces = List.copyOf(produces);
        }
    }
}
