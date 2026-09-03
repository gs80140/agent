package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import org.springframework.stereotype.Component;

import java.util.Map;

/** WRITE 能力：只有 Graph state 已产生 HumanApproved 事实后，计划才允许到达这里。 */
@Component
public class CreateTicketHandler implements CapabilityHandler {
    private final CustomerSupportTools tools;
    public CreateTicketHandler(CustomerSupportTools tools) { this.tools = tools; }
    @Override public CapabilitySchema schema() {
        return new CapabilitySchema("create-after-sale-ticket", "创建售后服务工单", "createTicket",
                java.util.List.of("userId", "orderId", "refundAllowed", "humanApproved", "serviceType", "reason"),
                java.util.List.of("ticketId", "ticketStatus"), CapabilitySchema.EffectLevel.WRITE, false);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 生产实现还应在工具/领域服务层基于 executionId 增加持久化幂等键。
        var response = tools.createSupportTicket(state.value("orderId", ""), state.value("userId", ""),
                state.value("serviceType", "REFUND"), state.value("reason", "用户申请售后"));
        if (response.ticketId() == null) throw new IllegalStateException(response.message());
        return Map.of("ticketId", response.ticketId(), "ticketStatus", response.status());
    }
}
