package com.example.supportagent.workflow;

import com.example.supportagent.ontology.OntologyDefinition;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.capability.ComposeResponseHandler;
import com.example.supportagent.workflow.capability.CreateTicketHandler;
import com.example.supportagent.workflow.capability.EvaluatePolicyHandler;
import com.example.supportagent.workflow.capability.ExtractRequestHandler;
import com.example.supportagent.workflow.capability.HumanApprovalHandler;
import com.example.supportagent.workflow.capability.NotifyCustomerHandler;
import com.example.supportagent.workflow.capability.QueryOrdersHandler;
import com.example.supportagent.workflow.capability.SelectOrderHandler;
import com.example.supportagent.workflow.trace.ExecutionTraceStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 覆盖从 Ontology 规划到 Graph 中断、审批恢复和副作用执行的完整核心链路。 */
class OntologyGraphRuntimeTest {

    @Test
    void plansInterruptsAndResumesAfterApproval() throws Exception {
        // 测试直接加载与生产 fallback 相同的知识文件，避免测试和实际 Ontology 漂移。
        OntologyDefinition ontology;
        try (var input = getClass().getResourceAsStream("/ontology/support-agent.yaml")) {
            ontology = new ObjectMapper(new YAMLFactory()).readValue(input, OntologyDefinition.class);
        }
        var tools = new CustomerSupportTools();
        var catalog = new CapabilityCatalog(java.util.List.of(
                new ExtractRequestHandler(), new QueryOrdersHandler(tools), new SelectOrderHandler(),
                new EvaluatePolicyHandler(tools), new HumanApprovalHandler(), new CreateTicketHandler(tools),
                new NotifyCustomerHandler(tools), new ComposeResponseHandler()));
        var planner = new OntologyCapabilityPlanner();
        var plan = planner.plan("我是张三，机械键盘按键连击失灵，帮我申请退货退款", ontology);
        new ExecutionPlanValidator(catalog).validate(plan);

        var traces = new ExecutionTraceStore();
        var runtime = new AgentGraphRuntime(traces);
        var waiting = runtime.start(new DynamicGraphCompiler(catalog, traces).compile(plan), plan,
                "我是张三，机械键盘按键连击失灵，帮我申请退货退款");

        // 首次调用只能完成只读阶段，不应创建工单。
        assertThat(waiting.status()).isEqualTo(AgentExecutionResponse.Status.WAITING_APPROVAL);
        assertThat(waiting.plannedCapabilities()).containsSubsequence(
                "QueryUserOrders", "EvaluateRefundPolicy", "RequestHumanApproval", "CreateSupportTicket");
        assertThat(traces.get(waiting.executionId()).status()).isEqualTo("WAITING_APPROVAL");
        assertThat(traces.get(waiting.executionId()).nodes())
                .filteredOn(node -> node.status().equals("COMPLETED")).isNotEmpty();

        // 批准后从 checkpoint 恢复，才会得到真实 ticketId 和最终回复。
        var completed = runtime.decide(waiting.executionId(), true);
        assertThat(completed.status()).isEqualTo(AgentExecutionResponse.Status.COMPLETED);
        assertThat(completed.content()).contains("TCK-", "无线机械键盘");
        assertThat(traces.get(waiting.executionId()).status()).isEqualTo("COMPLETED");
    }
}
