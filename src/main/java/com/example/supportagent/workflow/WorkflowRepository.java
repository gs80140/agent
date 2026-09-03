package com.example.supportagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/** 从企业流程库加载流程；Demo 使用 classpath，生产环境可替换为 Nacos/数据库/Git 发布仓库。 */
@Component
public class WorkflowRepository {
    private final ResourcePatternResolver resources;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    private volatile List<WorkflowDefinition> workflows = List.of();

    public WorkflowRepository(ResourcePatternResolver resources) { this.resources = resources; }

    @PostConstruct
    void loadPublishedWorkflows() throws IOException {
        var loaded = new java.util.ArrayList<WorkflowDefinition>();
        for (var resource : resources.getResources("classpath*:workflows/*.yaml")) {
            try (var input = resource.getInputStream()) {
                loaded.add(yaml.readValue(input, WorkflowDefinition.class));
            }
        }
        if (loaded.isEmpty()) throw new IllegalStateException("企业流程库为空");
        workflows = loaded.stream().sorted(Comparator.comparing(WorkflowDefinition::id)
                .thenComparing(WorkflowDefinition::version)).toList();
    }

    public WorkflowDefinition requirePublishedForIntent(String intentId) {
        var matches = workflows.stream().filter(w -> w.status() == WorkflowDefinition.Status.PUBLISHED)
                .filter(w -> w.appliesToIntents().contains(intentId)).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("意图必须唯一匹配一个已发布流程，intent=" + intentId
                    + ", matches=" + matches.size());
        }
        return matches.getFirst();
    }

    public WorkflowDefinition requirePublished(String workflowId, String version) {
        return workflows.stream().filter(w -> w.status() == WorkflowDefinition.Status.PUBLISHED)
                .filter(w -> w.id().equals(workflowId) && w.version().equals(version)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程不存在、未发布或版本不匹配: "
                        + workflowId + "@" + version));
    }

    public List<WorkflowDefinition> all() { return workflows; }
}
