package com.example.supportagent.knowledge;

import java.util.List;

/** 只为正在执行且明确需要证据的节点检索知识。 */
public interface KnowledgeRetriever {
    RetrievedKnowledge retrieve(String query, List<String> ontologyConceptIds);
}
