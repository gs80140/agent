package com.example.supportagent.workflow;

import com.example.supportagent.ontology.OntologyDefinition;
import com.example.supportagent.ontology.OntologyValidator;
import com.example.supportagent.ontology.ResolvedIntent;
import com.example.supportagent.knowledge.KnowledgeDocument;
import com.example.supportagent.knowledge.RetrievedKnowledge;
import com.example.supportagent.tools.CustomerSupportTools;
import com.example.supportagent.workflow.capability.ComposeResponseHandler;
import com.example.supportagent.workflow.capability.CollectEvidenceHandler;
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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 覆盖本体意图、企业已发布流程、节点知识检索、Graph 中断和审批恢复的核心链路。 */
class OntologyGraphRuntimeTest {

    @Test
    void plansInterruptsAndResumesAfterApproval() throws Exception {
        // 测试直接加载与生产 fallback 相同的知识文件，避免测试和实际 Ontology 漂移。
        OntologyDefinition ontology;
        try (var input = getClass().getResourceAsStream("/ontology/support-agent.yaml")) {
            ontology = new ObjectMapper(new YAMLFactory()).readValue(input, OntologyDefinition.class);
        }
        OntologyValidator.validateDefinition(ontology);
        var tools = new CustomerSupportTools();
        var knowledgeRetriever = (com.example.supportagent.knowledge.KnowledgeRetriever) (query, concepts) ->
                new RetrievedKnowledge(java.util.List.of(new KnowledgeDocument("product-quality-policy",
                        "商品质量政策", concepts, "七天内质量问题支持退货退款")), "测试知识证据");
        var catalog = new CapabilityCatalog(java.util.List.of(
                new ExtractRequestHandler(), new QueryOrdersHandler(tools), new SelectOrderHandler(),
                new CollectEvidenceHandler(), new EvaluatePolicyHandler(tools, knowledgeRetriever),
                new HumanApprovalHandler(), new CreateTicketHandler(tools),
                new NotifyCustomerHandler(tools), new ComposeResponseHandler()));
        var workflowRepository = new WorkflowRepository(new PathMatchingResourcePatternResolver());
        workflowRepository.loadPublishedWorkflows();
        var plan = new WorkflowResolver(workflowRepository, catalog).resolve(
                new ResolvedIntent("return-refund-intent",
                        java.util.List.of("key-chattering", "quality-problem"), "测试本体映射"), ontology);
        var exchangePlan = new WorkflowResolver(workflowRepository, catalog).resolve(
                new ResolvedIntent("exchange-intent", java.util.List.of("quality-problem"), "换货快速映射"), ontology);
        assertThat(exchangePlan.workflowId()).isEqualTo("standard-return-refund");
        new ExecutionPlanValidator(catalog).validate(plan);

        var traces = new ExecutionTraceStore();
        var runtime = new AgentGraphRuntime(traces, new InteractionInputValidator());
        var compiler = new DynamicGraphCompiler(catalog, traces, workflowRepository);
        var alteredPlan = new ExecutionPlan(plan.goal(), plan.ontologyVersion(), plan.workflowId(),
                plan.workflowVersion(), plan.knowledgeReferences(), plan.planningReasoning(),
                plan.nodes().subList(0, plan.nodes().size() - 1));
        assertThatThrownBy(() -> compiler.compile(alteredPlan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已发布 Workflow 节点序列不一致");

        var waitingInput = runtime.start(compiler.compile(plan), plan,
                "我是张三，机械键盘按键连击失灵，帮我申请退货退款");

        // 首次中断要求补充资料；提交资料后才继续执行政策判断并进入审批中断。
        assertThat(waitingInput.status()).isEqualTo(AgentExecutionResponse.Status.WAITING_INPUT);
        assertThat(waitingInput.interaction().type()).isEqualTo(HumanInteraction.Type.FORM_INPUT);
        assertThat(waitingInput.plannedCapabilities()).containsSubsequence(
                "查询客户身份和最近订单", "查询订单详情并判断售后政策",
                "请求客户确认即将执行的售后操作", "创建售后服务工单");
        assertThat(traces.get(waitingInput.executionId()).status()).isEqualTo("WAITING_INPUT");

        assertThatThrownBy(() -> runtime.submitInteraction(waitingInput.executionId(),
                waitingInput.interaction().interactionId(),
                Map.of("problemDescription", "按键一次会连续输入三次"), ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("图片或视频地址");

        var waitingApproval = runtime.submitInteraction(waitingInput.executionId(),
                waitingInput.interaction().interactionId(), Map.of(
                        "problemDescription", "按键一次会连续输入三次",
                        "evidenceUrls", java.util.List.of("https://example.test/evidence.jpg")), ignored -> { });
        assertThat(waitingApproval.status()).isEqualTo(AgentExecutionResponse.Status.WAITING_APPROVAL);
        assertThat(waitingApproval.interaction().type()).isEqualTo(HumanInteraction.Type.APPROVAL);
        assertThat(traces.get(waitingInput.executionId()).nodes())
                .filteredOn(node -> node.status().equals("COMPLETED")).isNotEmpty();
        assertThat(traces.get(waitingInput.executionId()).nodes()).filteredOn(node -> node.nodeId().equals("evaluate-policy"))
                .singleElement().satisfies(node -> assertThat(node.output()).containsKey("policyEvidenceIds"));

        // 批准后从 checkpoint 恢复，才会得到真实 ticketId 和最终回复。
        var completed = runtime.decide(waitingInput.executionId(), true);
        assertThat(completed.status()).isEqualTo(AgentExecutionResponse.Status.COMPLETED);
        assertThat(completed.content()).contains("TCK-", "无线机械键盘");
        assertThat(traces.get(waitingInput.executionId()).status()).isEqualTo("COMPLETED");

        // 超出售后期限属于正常业务终态，不应向前端暴露“Graph 恢复失败”，也不能进入审批和写节点。
        new ExecutionPlanValidator(catalog).validate(exchangePlan);
        var exchangeWaitingInput = runtime.start(compiler.compile(exchangePlan), exchangePlan,
                "我是张三，之前买的蓝牙耳机有杂音，可以换货吗？");
        var notEligible = runtime.submitInteraction(exchangeWaitingInput.executionId(),
                exchangeWaitingInput.interaction().interactionId(), Map.of(
                        "problemDescription", "蓝牙耳机播放音乐时持续出现杂音",
                        "evidenceUrls", java.util.List.of("https://example.test/noise.mp4")), ignored -> { });
        assertThat(notEligible.status()).isEqualTo(AgentExecutionResponse.Status.NOT_ELIGIBLE);
        assertThat(notEligible.content()).contains("当前不支持退货或换货", "申请保修");
        assertThat(traces.get(exchangeWaitingInput.executionId()).status()).isEqualTo("NOT_ELIGIBLE");
        assertThat(traces.get(exchangeWaitingInput.executionId()).nodes())
                .filteredOn(node -> node.nodeId().equals("create-ticket"))
                .singleElement().satisfies(node -> assertThat(node.status()).isEqualTo("PENDING"));
    }
}
