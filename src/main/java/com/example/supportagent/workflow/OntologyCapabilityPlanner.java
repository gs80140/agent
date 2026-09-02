package com.example.supportagent.workflow;

import com.example.supportagent.ontology.OntologyDefinition;
import com.example.supportagent.ontology.OntologyDefinition.CapabilityDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 Ontology 前置条件/执行效果的轻量反向规划器。
 *
 * <p>算法从目标事实开始，递归寻找能够产出该事实的 Capability；再继续满足该能力的
 * requires，最后利用 LinkedHashSet 得到去重且符合依赖顺序的执行计划。这不是固定流程：</n+ * 修改 Ontology 中的事实关系即可改变生成的节点序列。</p>
 */
@Component
public class OntologyCapabilityPlanner {

    /** 根据用户文字匹配目标，并为该目标生成一个确定性的执行计划。 */
    public ExecutionPlan plan(String userPrompt, OntologyDefinition ontology) {
        // Demo 使用触发词做目标识别；生产版可替换为结构化 LLM 意图识别，但规划算法无需改变。
        var goalEntry = ontology.goals().entrySet().stream()
                .filter(entry -> matches(userPrompt, entry.getValue().triggers()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前 Ontology 无法匹配该用户目标"));

        var ordered = new LinkedHashSet<String>();
        // UserRequestAvailable 是每次调用天然具备的初始事实，对应 Graph 输入 userPrompt。
        var available = new HashSet<>(Set.of("UserRequestAvailable"));
        for (String desiredFact : goalEntry.getValue().desiredFacts()) {
            resolveFact(desiredFact, ontology, available, ordered, new HashSet<>());
        }

        var nodes = new ArrayList<ExecutionPlan.PlanNode>();
        int index = 1;
        for (String capabilityName : ordered) {
            var capability = ontology.capabilities().get(capabilityName);
            nodes.add(toNode(index++, capabilityName, capability));
        }
        return new ExecutionPlan(goalEntry.getKey(), ontology.version(), nodes);
    }

    private void resolveFact(String fact, OntologyDefinition ontology, Set<String> available,
                             LinkedHashSet<String> ordered, Set<String> resolving) {
        if (available.contains(fact)) return;
        // resolving 保存当前递归栈，而不是所有访问节点，用于准确发现循环依赖。
        if (!resolving.add(fact)) throw new IllegalArgumentException("Ontology 存在循环事实依赖: " + fact);
        var producer = ontology.capabilities().entrySet().stream()
                .filter(entry -> entry.getValue().produces().contains(fact))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有能力可以产出事实: " + fact));
        for (String required : producer.getValue().requires()) {
            // 先递归安排前置能力，再加入当前能力，天然形成拓扑顺序。
            resolveFact(required, ontology, available, ordered, resolving);
        }
        ordered.add(producer.getKey());
        available.addAll(producer.getValue().produces());
        resolving.remove(fact);
    }

    private boolean matches(String prompt, List<String> triggers) {
        var normalized = prompt.toLowerCase(Locale.ROOT);
        return triggers.stream().anyMatch(normalized::contains);
    }

    private ExecutionPlan.PlanNode toNode(int index, String name, CapabilityDefinition capability) {
        return new ExecutionPlan.PlanNode("n" + index + "_" + name, name, capability.implementation(),
                capability.effect(), capability.approvalRequired(), capability.requires(), capability.produces());
    }
}
