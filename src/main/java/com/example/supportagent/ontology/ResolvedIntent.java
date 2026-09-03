package com.example.supportagent.ontology;

import java.util.List;

/** LLM 对用户语言做出的本体映射；它不包含也不能生成任何流程节点。 */
public record ResolvedIntent(String intentId, List<String> matchedConceptIds, String reasoning) {
    public ResolvedIntent {
        matchedConceptIds = matchedConceptIds == null ? List.of() : List.copyOf(matchedConceptIds);
    }
}
