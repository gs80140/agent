package com.example.supportagent.ontology;

import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/** 发布五元组本体前执行引用完整性与基本语义校验。 */
public final class OntologyValidator {
    private OntologyValidator() {}

    public static void validateDefinition(OntologyDefinition ontology) {
        if (ontology == null || !StringUtils.hasText(ontology.version())) {
            throw new IllegalArgumentException("Ontology version 不能为空");
        }
        if (ontology.concepts().isEmpty() || ontology.relations().isEmpty()
                || ontology.functions().isEmpty() || ontology.axioms().isEmpty()
                || ontology.instances().isEmpty()) {
            throw new IllegalArgumentException("Ontology 必须完整提供 C、R、F、A、I 五个集合");
        }
        Set<String> conceptIds = unique(ontology.concepts().stream().map(OntologyDefinition.Concept::id).toList(), "Concept");
        Set<String> relationIds = unique(ontology.relations().stream().map(OntologyDefinition.Relation::id).toList(), "Relation");
        unique(ontology.functions().stream().map(OntologyDefinition.OntologyFunction::id).toList(), "Function");
        unique(ontology.axioms().stream().map(OntologyDefinition.Axiom::id).toList(), "Axiom");
        unique(ontology.instances().stream().map(OntologyDefinition.Instance::id).toList(), "Instance");
        ontology.concepts().forEach(c -> c.parentIds().forEach(parent -> require(conceptIds, parent, "Concept parent")));
        ontology.relations().forEach(r -> {
            require(conceptIds, r.domain(), "Relation domain");
            require(conceptIds, r.range(), "Relation range");
        });
        ontology.functions().forEach(f -> {
            if (!f.deterministic()) throw new IllegalArgumentException("Ontology Function 必须是确定性的: " + f.id());
            f.inputConcepts().forEach(id -> require(conceptIds, id, "Function input"));
            require(conceptIds, f.outputConcept(), "Function output");
        });
        ontology.instances().forEach(i -> {
            require(conceptIds, i.type(), "Instance type");
            i.relations().keySet().forEach(id -> require(relationIds, id, "Instance relation"));
        });
    }

    private static Set<String> unique(java.util.List<String> ids, String type) {
        var unique = new HashSet<String>();
        ids.forEach(id -> {
            if (!StringUtils.hasText(id)) throw new IllegalArgumentException(type + " ID 不能为空");
            if (!unique.add(id)) throw new IllegalArgumentException(type + " ID 重复: " + id);
        });
        return unique;
    }

    private static void require(Set<String> ids, String value, String context) {
        if (!ids.contains(value)) throw new IllegalArgumentException(context + " 引用了不存在的 ID: " + value);
    }
}
