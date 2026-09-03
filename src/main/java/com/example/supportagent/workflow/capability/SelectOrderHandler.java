package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools.OrderSummary;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** NONE 副作用能力：从已查询订单中选择用户描述的目标商品。 */
@Component
public class SelectOrderHandler implements CapabilityHandler {
    @Override public CapabilitySchema schema() {
        return new CapabilitySchema("locate-target-order", "定位客户描述的目标订单", "selectOrder",
                java.util.List.of("orders", "targetProduct"),
                java.util.List.of("orderId", "productName", "amount"),
                CapabilitySchema.EffectLevel.NONE, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        // Graph state 对集合只保留运行时类型，因此在节点边界集中完成受控转换。
        var orders = (List<OrderSummary>) state.value("orders").orElse(List.of());
        String product = state.value("targetProduct", "");
        var selected = orders.stream().filter(order -> order.productName().contains(product.replace("无线", ""))
                        || product.contains(order.productName().replace("无线", "")))
                .findFirst().orElseThrow(() -> new IllegalStateException("未找到用户描述的目标订单"));
        return Map.of("orderId", selected.orderId(), "productName", selected.productName(), "amount", selected.amount());
    }
}
