package com.example.supportagent.workflow;

import java.util.List;

/**
 * 由开发团队维护的确定性机器契约，不属于企业自然语言 Ontology。
 *
 * <p>Capability Bean 自己声明业务名称及“执行时必须有哪些字段、会产生哪些字段、
 * 是否有副作用”。Ontology、Workflow、LLM 和 Nacos 均无权修改这份机器契约。</p>
 */
public record CapabilitySchema(
        String id,
        String name,
        String implementation,
        List<String> requiredInputs,
        List<String> outputs,
        EffectLevel effect,
        boolean approvalRequired) {

    public CapabilitySchema {
        requiredInputs = List.copyOf(requiredInputs);
        outputs = List.copyOf(outputs);
    }

    public enum EffectLevel { NONE, READ, WRITE, EXTERNAL_WRITE }
}
