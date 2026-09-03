package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.Map;

/**
 * Ontology Capability 与 Spring AI Alibaba Graph 节点之间的适配协议。
 * schema 提供稳定业务 ID 和机器契约，apply 是 Graph 实际执行的业务动作。
 */
public interface CapabilityHandler extends NodeAction {
    CapabilitySchema schema();

    /** 默认忽略流程元数据；需要节点级知识证据的能力可以覆盖。 */
    default Map<String, Object> execute(OverAllState state, ExecutionPlan.PlanNode node) throws Exception {
        return apply(state);
    }
}
