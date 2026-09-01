package com.example.supportagent.tools;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSupportToolsTest {

    private final CustomerSupportTools tools = new CustomerSupportTools(
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void completesTheHappyPathDeterministically() {
        var user = tools.getUserOrders("张三");
        var detail = tools.getOrderDetail("ORD-202601");
        var ticket = tools.createSupportTicket("ORD-202601", user.userId(), "REFUND", "按键连击失灵");
        var notification = tools.sendCustomerNotification(user.userId(), ticket.ticketId(), "退款申请已提交");

        assertThat(user.orders()).extracting(CustomerSupportTools.OrderSummary::productName)
                .contains("无线机械键盘");
        assertThat(detail.canRefund()).isTrue();
        assertThat(ticket.ticketId()).isEqualTo("TCK-1788177600000");
        assertThat(ticket.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(notification.sent()).isTrue();
    }

    @Test
    void rejectsUnknownUserAndInvalidTicketNotification() {
        assertThat(tools.getUserOrders("李四").orders()).isEmpty();
        assertThat(tools.sendCustomerNotification("U0", "bad-id", "test").sent()).isFalse();
    }

    @Test
    void businessRuleRejectsRefundAfterTheReturnWindow() {
        var ticket = tools.createSupportTicket("ORD-202602", "U10086", "REFUND", "不想要了");

        assertThat(ticket.status()).isEqualTo("REJECTED");
        assertThat(ticket.ticketId()).isNull();
    }
}
