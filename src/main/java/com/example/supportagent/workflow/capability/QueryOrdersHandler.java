package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** READ 能力：根据解析出的用户标识查询客户及其订单，并把结果写入 Graph state。 */
@Component
public class QueryOrdersHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public QueryOrdersHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public String name() { return "queryOrders"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 工具返回空 userId 表示业务未命中；转为异常可阻止后续选单和写操作。
        var response = tools.getUserOrders(state.value("userIdentifier", ""));
        if (response.userId() == null) throw new IllegalStateException(response.message());
        return Map.of("userId", response.userId(), "userName", response.userName(), "orders", response.orders());
    }
}
