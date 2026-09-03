package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import org.springframework.stereotype.Component;

import java.util.Map;

/** EXTERNAL_WRITE 能力：仅在工单创建成功并得到 ticketId 后发送客户通知。 */
@Component
public class NotifyCustomerHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public NotifyCustomerHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public CapabilitySchema schema() {
        return new CapabilitySchema("notify-customer-progress", "通知客户售后办理进度", "notifyCustomer",
                java.util.List.of("userId", "ticketId"),
                java.util.List.of("notificationSent", "notificationMessage"),
                CapabilitySchema.EffectLevel.EXTERNAL_WRITE, false);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 通知失败抛出异常，避免最终回复误报“已通知”。生产环境可改为重试/补偿节点。
        var response = tools.sendCustomerNotification(state.value("userId", ""), state.value("ticketId", ""),
                "售后申请已创建");
        if (!response.sent()) throw new IllegalStateException(response.message());
        return Map.of("notificationSent", true, "notificationMessage", response.message());
    }
}
