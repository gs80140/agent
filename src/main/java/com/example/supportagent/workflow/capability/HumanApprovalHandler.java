package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HumanApprovalHandler implements CapabilityHandler {
    @Override public String name() { return "humanApproval"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String decision = state.value("approvalDecision", "");
        if (!"APPROVE".equals(decision)) throw new IllegalStateException("未获得人工批准，禁止执行写操作");
        return Map.of("humanApproved", true);
    }
}
