package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** WRITE 能力：只有 Graph state 已产生 HumanApproved 事实后，计划才允许到达这里。 */
@Component
public class CreateTicketHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public CreateTicketHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public String name() { return "createTicket"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 生产实现还应在工具/领域服务层基于 executionId 增加持久化幂等键。
        var response = tools.createSupportTicket(state.value("orderId", ""), state.value("userId", ""),
                state.value("serviceType", "REFUND"), state.value("reason", "用户申请售后"));
        if (response.ticketId() == null) throw new IllegalStateException(response.message());
        return Map.of("ticketId", response.ticketId(), "ticketStatus", response.status());
    }
}
