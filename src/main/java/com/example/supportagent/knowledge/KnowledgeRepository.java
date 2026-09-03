package com.example.supportagent.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Demo 的独立知识库；接口边界允许生产环境替换成 VectorStore、Elasticsearch 或知识图谱。 */
@Component
public class KnowledgeRepository {
    private final ResourceLoader resources;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private volatile List<KnowledgeDocument> documents = List.of();

    public KnowledgeRepository(ResourceLoader resources) { this.resources = resources; }

    @PostConstruct
    void load() throws IOException {
        try (var input = resources.getResource("classpath:knowledge/after-sale.yaml").getInputStream()) {
            documents = List.copyOf(yaml.readValue(input, new TypeReference<List<KnowledgeDocument>>() {}));
        }
        if (documents.isEmpty()) throw new IllegalStateException("企业知识库为空");
    }

    public List<KnowledgeDocument> candidatesForConcepts(List<String> conceptIds) {
        if (conceptIds == null || conceptIds.isEmpty()) return documents;
        return documents.stream().filter(d -> d.conceptIds().stream().anyMatch(conceptIds::contains)).toList();
    }
}
