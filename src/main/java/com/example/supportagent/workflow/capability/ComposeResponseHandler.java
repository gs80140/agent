package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ComposeResponseHandler implements CapabilityHandler {
    @Override public String name() { return "composeResponse"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String content = "您好，%s！已为您的 **%s**（订单号：`%s`）创建售后工单 `%s`。\n\n"
                .formatted(state.value("userName", "用户"), state.value("productName", "商品"),
                        state.value("orderId", ""), state.value("ticketId", ""))
                + "当前状态：`" + state.value("ticketStatus", "PENDING_APPROVAL") + "`。"
                + state.value("notificationMessage", "通知已发送") + "。";
        return Map.of("finalResponse", content);
    }
}
