package com.example.supportagent.knowledge;

import java.util.List;

/** 某个 Graph 节点从知识库实际取回的证据。 */
public record RetrievedKnowledge(List<KnowledgeDocument> documents, String reasoning) {
    public RetrievedKnowledge { documents = documents == null ? List.of() : List.copyOf(documents); }
}
