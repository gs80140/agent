package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryOrdersHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public QueryOrdersHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public String name() { return "queryOrders"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var response = tools.getUserOrders(state.value("userIdentifier", ""));
        if (response.userId() == null) throw new IllegalStateException(response.message());
        return Map.of("userId", response.userId(), "userName", response.userName(), "orders", response.orders());
    }
}
