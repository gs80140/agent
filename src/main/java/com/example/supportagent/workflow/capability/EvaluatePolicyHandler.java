package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EvaluatePolicyHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public EvaluatePolicyHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public String name() { return "evaluatePolicy"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var detail = tools.getOrderDetail(state.value("orderId", ""));
        if (!detail.canRefund()) throw new IllegalStateException("当前订单不符合退款/换货政策：" + detail.policy());
        return Map.of("refundAllowed", true, "signedDate", detail.signedDate().toString(),
                "logisticsStatus", detail.logisticsStatus(), "policy", detail.policy());
    }
}
