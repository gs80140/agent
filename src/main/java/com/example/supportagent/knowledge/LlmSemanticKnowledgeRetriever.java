package com.example.supportagent.knowledge;

import com.example.supportagent.config.NacosPromptRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 对独立知识库的候选文档做语义筛选；检索结果只作为节点决策证据，不决定流程。 */
@Component
public class LlmSemanticKnowledgeRetriever implements KnowledgeRetriever {
    private static final String PROMPT_NAME = "knowledge-retriever";
    private static final String DEFAULT_PROMPT = """
            你是企业知识检索器。根据当前业务查询，从候选文档中选择真正相关的证据，
            只能返回候选 documentId，不得生成流程、能力或虚构政策。
            """;
    private final ChatClient chatClient;
    private final NacosPromptRegistry prompts;
    private final KnowledgeRepository repository;

    public LlmSemanticKnowledgeRetriever(ChatClient.Builder builder, NacosPromptRegistry prompts,
                                         KnowledgeRepository repository) {
        this.chatClient = builder.build();
        this.prompts = prompts;
        this.repository = repository;
    }

    @Override
    public RetrievedKnowledge retrieve(String query, List<String> ontologyConceptIds) {
        var candidates = repository.candidatesForConcepts(ontologyConceptIds);
        if (candidates.isEmpty()) throw new IllegalArgumentException("没有与本体概念关联的企业知识");
        String corpus = candidates.stream().map(d -> "[%s] %s\n%s".formatted(d.id(), d.title(), d.content()))
                .collect(Collectors.joining("\n\n"));
        var selection = chatClient.prompt().system(prompts.getOrDefault(PROMPT_NAME, DEFAULT_PROMPT))
                .user("当前业务查询：\n" + query + "\n\n候选知识文档：\n" + corpus)
                .call().entity(Selection.class, spec -> spec.validateSchema());
        if (selection == null) throw new IllegalStateException("知识检索未返回结果");
        Set<String> ids = Set.copyOf(selection.documentIds());
        var selected = candidates.stream().filter(d -> ids.contains(d.id())).toList();
        if (selected.isEmpty()) throw new IllegalStateException("知识检索没有命中有效文档");
        return new RetrievedKnowledge(selected, selection.reasoning());
    }

    public record Selection(List<String> documentIds, String reasoning) {
        public Selection { documentIds = documentIds == null ? List.of() : List.copyOf(documentIds); }
    }
}
