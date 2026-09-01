package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools.OrderSummary;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SelectOrderHandler implements CapabilityHandler {
    @Override public String name() { return "selectOrder"; }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        var orders = (List<OrderSummary>) state.value("orders").orElse(List.of());
        String product = state.value("targetProduct", "");
        var selected = orders.stream().filter(order -> order.productName().contains(product.replace("无线", ""))
                        || product.contains(order.productName().replace("无线", "")))
                .findFirst().orElseThrow(() -> new IllegalStateException("未找到用户描述的目标订单"));
        return Map.of("orderId", selected.orderId(), "productName", selected.productName(), "amount", selected.amount());
    }
}
