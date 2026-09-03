package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** FORM_INPUT 恢复后执行：再次确认资料确实已经进入 checkpoint，并产出后续政策节点所需事实。 */
@Component
public class CollectEvidenceHandler implements CapabilityHandler {
    @Override
    public CapabilitySchema schema() {
        return new CapabilitySchema("collect-return-evidence", "接收并确认客户补充资料", "collectEvidence",
                List.of("problemDescription", "evidenceUrls"),
                List.of("evidenceCompleted", "evidenceSummary"), CapabilitySchema.EffectLevel.NONE, false);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String description = state.value("problemDescription", "").trim();
        Object urls = state.value("evidenceUrls").orElse(List.of());
        if (description.length() < 5 || !(urls instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("客户补充资料未通过执行节点复核");
        }
        return Map.of("evidenceCompleted", true,
                "evidenceSummary", description + "；证据数量=" + list.size());
    }
}
