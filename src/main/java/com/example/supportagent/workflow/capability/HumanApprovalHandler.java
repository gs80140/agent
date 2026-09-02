package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 人工审批节点的执行体。
 * Graph 在该节点之前 interrupt；恢复后本节点再次校验 checkpoint 中的决定，形成双重门禁。
 */
@Component
public class HumanApprovalHandler implements CapabilityHandler {
    @Override public String name() { return "humanApproval"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 即使有人错误地直接恢复 Graph，没有明确 APPROVE 也不能产出 HumanApproved 事实。
        String decision = state.value("approvalDecision", "");
        if (!"APPROVE".equals(decision)) throw new IllegalStateException("未获得人工批准，禁止执行写操作");
        return Map.of("humanApproved", true);
    }
}
