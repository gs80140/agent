package com.example.supportagent.knowledge;

import java.util.List;

/** 知识库文档；通过 conceptIds 与 Ontology 建立语义链接，但正文不进入本体。 */
public record KnowledgeDocument(String id, String title, List<String> conceptIds, String content) {
    public KnowledgeDocument { conceptIds = conceptIds == null ? List.of() : List.copyOf(conceptIds); }
}
