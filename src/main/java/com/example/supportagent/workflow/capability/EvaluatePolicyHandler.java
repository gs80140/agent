package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** READ 能力：查询订单签收信息并把售后政策判断转化为可供规划使用的事实。 */
@Component
public class EvaluatePolicyHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public EvaluatePolicyHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public String name() { return "evaluatePolicy"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var detail = tools.getOrderDetail(state.value("orderId", ""));
        // 不符合政策时快速失败，Graph 不可能到达审批和写操作节点。
        if (!detail.canRefund()) throw new IllegalStateException("当前订单不符合退款/换货政策：" + detail.policy());
        return Map.of("refundAllowed", true, "signedDate", detail.signedDate().toString(),
                "logisticsStatus", detail.logisticsStatus(), "policy", detail.policy());
    }
}
