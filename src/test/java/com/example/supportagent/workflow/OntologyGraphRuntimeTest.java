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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyGraphRuntimeTest {

    @Test
    void plansInterruptsAndResumesAfterApproval() throws Exception {
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

        var runtime = new AgentGraphRuntime();
        var waiting = runtime.start(new DynamicGraphCompiler(catalog).compile(plan), plan,
                "我是张三，机械键盘按键连击失灵，帮我申请退货退款");

        assertThat(waiting.status()).isEqualTo(AgentExecutionResponse.Status.WAITING_APPROVAL);
        assertThat(waiting.plannedCapabilities()).containsSubsequence(
                "QueryUserOrders", "EvaluateRefundPolicy", "RequestHumanApproval", "CreateSupportTicket");

        var completed = runtime.decide(waiting.executionId(), true);
        assertThat(completed.status()).isEqualTo(AgentExecutionResponse.Status.COMPLETED);
        assertThat(completed.content()).contains("TCK-", "无线机械键盘");
    }
}
