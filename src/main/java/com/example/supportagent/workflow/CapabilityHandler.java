package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.action.NodeAction;

/**
 * Ontology Capability 与 Spring AI Alibaba Graph 节点之间的适配协议。
 * name 是 Ontology 中 implementation 的白名单键，apply 是 Graph 实际执行的业务动作。
 */
public interface CapabilityHandler extends NodeAction {
    String name();
}
