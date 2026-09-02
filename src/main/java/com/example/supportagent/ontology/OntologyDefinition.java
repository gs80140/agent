package com.example.supportagent.ontology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业 Ontology 的轻量化内存模型。
 *
 * <p>它不直接保存一条固定工作流，而是描述“目标需要哪些事实”以及“每个能力需要和产生
 * 哪些事实”。{@code OntologyCapabilityPlanner} 据此在运行时反向推导执行步骤。</p>
 *
 * @param version Ontology 版本；每个执行实例都会固定该版本，避免恢复时语义漂移
 * @param goals 用户可能提出的业务目标
 * @param capabilities 企业允许 Agent 使用的能力知识
 */
public record OntologyDefinition(
        String version,
        Map<String, GoalDefinition> goals,
        Map<String, CapabilityDefinition> capabilities) {

    public OntologyDefinition {
        // 防御性复制保证发布后的 Ontology 快照不可变，可安全地被并发请求共享。
        goals = goals == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(goals));
        capabilities = capabilities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(capabilities));
    }

    /** 目标定义：触发词用于 Demo 意图匹配，desiredFacts 是规划的终点。 */
    public record GoalDefinition(List<String> triggers, List<String> desiredFacts) {
        public GoalDefinition {
            triggers = triggers == null ? List.of() : List.copyOf(triggers);
            desiredFacts = desiredFacts == null ? List.of() : List.copyOf(desiredFacts);
        }
    }

    /**
     * 能力的语义定义。
     * implementation 只保存受控实现名称，不接受类名、脚本或 SpEL，避免配置侧任意执行代码。
     */
    public record CapabilityDefinition(
            String implementation,
            List<String> requires,
            List<String> produces,
            EffectLevel effect,
            boolean approvalRequired,
            String description) {
        public CapabilityDefinition {
            requires = requires == null ? List.of() : List.copyOf(requires);
            produces = produces == null ? List.of() : List.copyOf(produces);
            effect = effect == null ? EffectLevel.NONE : effect;
        }
    }

    /** 能力的副作用等级，计划校验器据此强制写操作前必须存在人工审批。 */
    public enum EffectLevel { NONE, READ, WRITE, EXTERNAL_WRITE }
}
