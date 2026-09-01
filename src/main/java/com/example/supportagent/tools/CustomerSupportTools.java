package com.example.supportagent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class CustomerSupportTools {

    private static final Logger log = LoggerFactory.getLogger(CustomerSupportTools.class);
    private final Clock clock;

    private final Map<String, UserOrdersResponse> users = Map.of(
            "张三", sampleUser(),
            "13800138000", sampleUser()
    );

    public CustomerSupportTools() {
        this(Clock.systemUTC());
    }

    CustomerSupportTools(Clock clock) {
        this.clock = clock;
    }

    @Tool(name = "get_user_orders", description = "根据用户姓名或手机号，查询用户基本信息及最近历史订单。处理售后前必须先调用。")
    public UserOrdersResponse getUserOrders(
            @ToolParam(description = "用户手机号或用户姓名") String userIdentifier) {
        log.info("[Tool 1: get_user_orders] 查询用户订单，标识={}", userIdentifier);
        var response = users.get(userIdentifier);
        if (response == null) {
            return new UserOrdersResponse(null, null, List.of(), "未找到该用户，请核对姓名或手机号");
        }
        return response;
    }

    @Tool(name = "get_order_detail", description = "根据订单号查询签收时间、物流状态及当前是否支持退款/换货。创建售后工单前必须调用。")
    public OrderDetailResponse getOrderDetail(
            @ToolParam(description = "订单编号，例如 ORD-202601") String orderId) {
        log.info("[Tool 2: get_order_detail] 查询订单详情，订单号={}", orderId);
        return switch (orderId) {
            case "ORD-202601" -> new OrderDetailResponse(orderId, LocalDate.of(2026, 8, 25), true,
                    "已签收（6天前）", "7天内质量问题支持退货退款或换货");
            case "ORD-202602" -> new OrderDetailResponse(orderId, LocalDate.of(2026, 7, 1), false,
                    "已签收（超过30天）", "已超过无理由退换期，可申请保修检测");
            default -> new OrderDetailResponse(orderId, null, false, "订单不存在", "无法申请售后");
        };
    }

    @Tool(name = "create_support_ticket", description = "为符合政策的订单创建退款、换货或维修工单。只有查询订单详情并确认政策后才能调用。")
    public CreateTicketResponse createSupportTicket(
            @ToolParam(description = "订单编号") String orderId,
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "售后类型，只能是 REFUND、EXCHANGE 或 REPAIR") String serviceType,
            @ToolParam(description = "用户描述的申请原因") String reason) {
        log.info("[Tool 3: create_support_ticket] 创建工单，orderId={}, userId={}, type={}", orderId, userId, serviceType);
        if (!List.of("REFUND", "EXCHANGE", "REPAIR").contains(serviceType)) {
            return new CreateTicketResponse(null, "REJECTED", "不支持的售后类型");
        }
        if (!"U10086".equals(userId) || !List.of("ORD-202601", "ORD-202602").contains(orderId)) {
            return new CreateTicketResponse(null, "REJECTED", "用户与订单不匹配");
        }
        if ("ORD-202602".equals(orderId) && !"REPAIR".equals(serviceType)) {
            return new CreateTicketResponse(null, "REJECTED", "订单已超过退换期，仅支持申请维修检测");
        }
        var ticketId = "TCK-" + Instant.now(clock).toEpochMilli();
        return new CreateTicketResponse(ticketId, "PENDING_APPROVAL", "售后工单创建成功，已提交财务与仓库审核");
    }

    @Tool(name = "send_customer_notification", description = "在售后工单成功创建后，向用户发送处理进度通知。没有有效工单号时不得调用。")
    public NotificationResponse sendCustomerNotification(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "成功创建的售后工单号") String ticketId,
            @ToolParam(description = "通知内容的简短摘要") String message) {
        log.info("[Tool 4: send_customer_notification] 发送通知，userId={}, ticketId={}", userId, ticketId);
        if (ticketId == null || !ticketId.startsWith("TCK-")) {
            return new NotificationResponse(false, "无效工单号，通知未发送");
        }
        return new NotificationResponse(true, "短信通知已发送至用户绑定手机");
    }

    private static UserOrdersResponse sampleUser() {
        return new UserOrdersResponse("U10086", "张三", List.of(
                new OrderSummary("ORD-202601", "无线机械键盘", "DELIVERED", 399.0),
                new OrderSummary("ORD-202602", "降噪蓝牙耳机", "DELIVERED", 799.0)
        ), "查询成功");
    }

    public record OrderSummary(String orderId, String productName, String status, double amount) {}

    public record UserOrdersResponse(String userId, String userName, List<OrderSummary> orders, String message) {}

    public record OrderDetailResponse(String orderId, LocalDate signedDate, boolean canRefund,
                                      String logisticsStatus, String policy) {}

    public record CreateTicketResponse(String ticketId, String status, String message) {}

    public record NotificationResponse(boolean sent, String message) {}
}
