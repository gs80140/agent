package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 最终展示节点：只读取已被前序能力验证过的状态，不再产生外部副作用。 */
@Component
public class ComposeResponseHandler implements CapabilityHandler {
    @Override public CapabilitySchema schema() {
        return new CapabilitySchema("compose-customer-response", "汇总并回复售后处理结果", "composeResponse",
                java.util.List.of("userName", "productName", "orderId", "ticketId", "ticketStatus"),
                java.util.List.of("finalResponse"), CapabilitySchema.EffectLevel.NONE, false);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // Demo 使用模板保证结果可测试；可替换为读取 Nacos Prompt 的 ChatClient 节点。
        String content = "您好，%s！已为您的 **%s**（订单号：`%s`）创建售后工单 `%s`。\n\n"
                .formatted(state.value("userName", "用户"), state.value("productName", "商品"),
                        state.value("orderId", ""), state.value("ticketId", ""))
                + "当前状态：`" + state.value("ticketStatus", "PENDING_APPROVAL") + "`。"
                + state.value("notificationMessage", "通知已发送") + "。";
        return Map.of("finalResponse", content);
    }
}
