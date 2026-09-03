package com.example.supportagent.ontology;

/** 将自然语言诉求映射到本体中已经存在的业务意图概念。 */
public interface OntologyIntentResolver {
    ResolvedIntent resolve(String userPrompt, OntologyDefinition ontology);
}
