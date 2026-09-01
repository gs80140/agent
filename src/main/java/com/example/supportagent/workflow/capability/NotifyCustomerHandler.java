package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotifyCustomerHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public NotifyCustomerHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public String name() { return "notifyCustomer"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var response = tools.sendCustomerNotification(state.value("userId", ""), state.value("ticketId", ""),
                "售后申请已创建");
        if (!response.sent()) throw new IllegalStateException(response.message());
        return Map.of("notificationSent", true, "notificationMessage", response.message());
    }
}
