package com.example.supportagent.ontology;

import java.util.List;
import java.util.Map;

/** 严格对应领域本体五元组 O=(C,R,F,A,I) 的不可变快照。 */
public record OntologyDefinition(
        String version,
        List<Concept> concepts,
        List<Relation> relations,
        List<OntologyFunction> functions,
        List<Axiom> axioms,
        List<Instance> instances) {

    public OntologyDefinition {
        concepts = immutable(concepts);
        relations = immutable(relations);
        functions = immutable(functions);
        axioms = immutable(axioms);
        instances = immutable(instances);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** C：领域概念、上位概念和企业维护的自然语言识别词；识别词是概念注释，不是执行规则。 */
    public record Concept(String id, String name, String description, List<String> parentIds,
                          List<String> recognitionTerms) {
        public Concept {
            parentIds = immutable(parentIds);
            recognitionTerms = immutable(recognitionTerms);
        }
    }

    /** R：带 domain/range 的语义关系定义。 */
    public record Relation(String id, String name, String domain, String range, String description) {}

    /** F：同样输入必须得到同样结果的确定性业务函数。 */
    public record OntologyFunction(String id, String name, List<String> inputConcepts,
                                   String outputConcept, boolean deterministic, String description) {
        public OntologyFunction { inputConcepts = immutable(inputConcepts); }
    }

    /** A：分类、基数、互斥、流程偏序等不可违反的领域公理。 */
    public record Axiom(String id, String name, String type, String expression, String description) {}

    /** I：概念的正式实例；流程拓扑本身由 WorkflowRepository 保存。 */
    public record Instance(String id, String type, String name, Map<String, Object> properties,
                           Map<String, List<String>> relations) {
        public Instance {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
            relations = relations == null ? Map.of() : Map.copyOf(relations);
        }
    }
}
