package com.example.supportagent.ontology;

import com.example.supportagent.config.NacosPromptRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 混合式本体意图解析器：企业识别词本地快速命中，模糊表达才交给 LLM，并缓存相同版本下的结果。
 * LLM 仍然只做 Ontology Concept 映射，无权生成流程或选择 Capability。
 */
@Component
public class LlmOntologyIntentResolver implements OntologyIntentResolver {
    private static final String PROMPT_NAME = "ontology-intent-resolver";
    private static final String DEFAULT_PROMPT = """
            你是企业售后领域的本体映射器。把用户表达映射到候选 Ontology 业务意图概念，
            只能返回候选中的 intentId；matchedConceptIds 也只能引用给定概念。
            你只识别语义，不得生成、排列或建议任何工作流步骤和工具调用。
            """;
    private final ChatClient chatClient;
    private final NacosPromptRegistry prompts;
    private final Map<String, ResolvedIntent> cache = new ConcurrentHashMap<>();

    public LlmOntologyIntentResolver(ChatClient.Builder builder, NacosPromptRegistry prompts) {
        this.chatClient = builder.build();
        this.prompts = prompts;
    }

    @Override
    public ResolvedIntent resolve(String userPrompt, OntologyDefinition ontology) {
        String normalized = userPrompt == null ? "" : userPrompt.strip().replaceAll("\\s+", " ");
        String cacheKey = ontology.version() + "\n" + normalized;
        ResolvedIntent cached = cache.get(cacheKey);
        if (cached != null) return cached;

        Set<String> conceptIds = ontology.concepts().stream().map(OntologyDefinition.Concept::id)
                .collect(Collectors.toSet());
        // 只有被 BusinessGoal 实例引用的叶子意图才是可执行候选，父概念 business-intent 永不进入 LLM 候选。
        Set<String> executableIntentIds = ontology.instances().stream()
                .map(i -> i.properties().get("intentId"))
                .filter(java.util.Objects::nonNull).map(String::valueOf).collect(Collectors.toSet());
        var executableIntents = ontology.concepts().stream()
                .filter(c -> executableIntentIds.contains(c.id())).toList();
        if (executableIntents.isEmpty()) throw new IllegalArgumentException("Ontology 没有可执行的业务意图");

        ResolvedIntent fast = resolveByRecognitionTerms(normalized, executableIntents);
        if (fast != null) return remember(cacheKey, fast);

        var intentCandidates = executableIntents.stream()
                .map(c -> "[%s] %s：%s".formatted(c.id(), c.name(), c.description()))
                .collect(Collectors.joining("\n"));
        var concepts = ontology.concepts().stream()
                .map(c -> "[%s] %s：%s".formatted(c.id(), c.name(), c.description()))
                .collect(Collectors.joining("\n"));
        var result = chatClient.prompt().system(prompts.getOrDefault(PROMPT_NAME, DEFAULT_PROMPT))
                .user("用户诉求：\n" + userPrompt + "\n\n业务意图候选：\n" + intentCandidates
                        + "\n\n领域概念：\n" + concepts)
                .call().entity(ResolvedIntent.class, spec -> spec.validateSchema());
        if (result == null || !conceptIds.contains(result.intentId())
                || result.matchedConceptIds().stream().anyMatch(id -> !conceptIds.contains(id))) {
            throw new IllegalArgumentException("LLM 返回了 Ontology 中不存在的概念");
        }
        if (!executableIntentIds.contains(result.intentId())) {
            throw new IllegalArgumentException("识别结果不是可执行的业务意图: " + result.intentId());
        }
        return remember(cacheKey, result);
    }

    /** 唯一最高分才快速返回；冲突、无命中交由 LLM 消歧，避免规则强猜。 */
    private ResolvedIntent resolveByRecognitionTerms(String prompt, List<OntologyDefinition.Concept> candidates) {
        record Scored(OntologyDefinition.Concept concept, long score) {}
        var scored = candidates.stream().map(concept -> new Scored(concept,
                        concept.recognitionTerms().stream().filter(prompt::contains).count()))
                .filter(item -> item.score() > 0).sorted((a, b) -> Long.compare(b.score(), a.score())).toList();
        if (scored.isEmpty() || (scored.size() > 1 && scored.get(0).score() == scored.get(1).score())) return null;
        var winner = scored.getFirst();
        return new ResolvedIntent(winner.concept().id(), List.of(winner.concept().id()),
                "命中企业 Ontology 自然语言识别词（本地快速路径）");
    }

    private ResolvedIntent remember(String key, ResolvedIntent value) {
        // Demo 使用有界近似缓存；生产环境可以替换为 Caffeine 的 size/TTL 策略和命中率指标。
        if (cache.size() >= 1_000) cache.clear();
        cache.put(key, value);
        return value;
    }
}
