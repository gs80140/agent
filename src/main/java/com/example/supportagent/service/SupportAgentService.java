package com.example.supportagent.service;

import com.example.supportagent.ontology.EnterpriseOntologyRegistry;
import com.example.supportagent.ontology.OntologyIntentResolver;
import com.example.supportagent.workflow.AgentExecutionResponse;
import com.example.supportagent.workflow.AgentStreamEvent;
import com.example.supportagent.workflow.AgentGraphRuntime;
import com.example.supportagent.workflow.DynamicGraphCompiler;
import com.example.supportagent.workflow.ExecutionPlanValidator;
import com.example.supportagent.workflow.WorkflowResolver;
import com.example.supportagent.workflow.trace.ExecutionTraceSnapshot;
import com.example.supportagent.workflow.trace.ExecutionTraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 售后 Agent 的应用服务门面。
 *
 * <p>这里只负责串联“固定 Ontology 快照 → 意图映射 → 企业流程解析 → 校验 → 编译 → 执行”，不包含具体业务规则，
 * 从而避免原来的单体 Service 同时承担提示词、工具选择和流程控制。</p>
 */
@Service
public class SupportAgentService {
    private static final Logger log = LoggerFactory.getLogger(SupportAgentService.class);

    private final EnterpriseOntologyRegistry ontologyRegistry;
    private final OntologyIntentResolver intentResolver;
    private final WorkflowResolver workflowResolver;
    private final ExecutionPlanValidator validator;
    private final DynamicGraphCompiler graphCompiler;
    private final AgentGraphRuntime graphRuntime;
    private final ExecutionTraceStore traceStore;

    public SupportAgentService(EnterpriseOntologyRegistry ontologyRegistry,
                               OntologyIntentResolver intentResolver, WorkflowResolver workflowResolver,
                               ExecutionPlanValidator validator, DynamicGraphCompiler graphCompiler,
                               AgentGraphRuntime graphRuntime, ExecutionTraceStore traceStore) {
        this.ontologyRegistry = ontologyRegistry;
        this.intentResolver = intentResolver;
        this.workflowResolver = workflowResolver;
        this.validator = validator;
        this.graphCompiler = graphCompiler;
        this.graphRuntime = graphRuntime;
        this.traceStore = traceStore;
    }

    /** 为一条新的用户诉求创建并启动 execution。 */
    public AgentExecutionResponse start(String userPrompt) {
        return start(userPrompt, ignored -> { });
    }

    /**
     * 启动 Agent，并把耗时阶段与 Graph 节点进度实时交给 SSE 等上层通道。
     * Consumer 只承担观察职责，不参与任何业务决策。
     */
    public AgentExecutionResponse start(String userPrompt, Consumer<AgentStreamEvent> events) {
        // 在请求入口读取一次快照，后续 plan/graph 都固定使用同一个 ontologyVersion。
        events.accept(AgentStreamEvent.progress("ONTOLOGY_LOADING", "正在读取企业知识快照…"));
        var ontology = ontologyRegistry.current();
        events.accept(AgentStreamEvent.progress("ONTOLOGY_INTENT_RESOLUTION",
                "正在把用户诉求映射到企业 Ontology 业务概念…"));
        // nanoTime 是单调时钟，适合统计耗时，不受系统时间校准或时区变化影响。
        long intentStartedAt = System.nanoTime();
        var intent = intentResolver.resolve(userPrompt, ontology);
        long intentElapsedMs = (System.nanoTime() - intentStartedAt) / 1_000_000;
        log.info("Ontology 意图识别完成，intentId={}, ontologyVersion={}, elapsedMs={}",
                intent.intentId(), ontology.version(), intentElapsedMs);
        events.accept(AgentStreamEvent.progress("ONTOLOGY_INTENT_RESOLVED",
                "已识别本体意图：%s（耗时 %d ms）".formatted(intent.intentId(), intentElapsedMs),
                null, intent));
        events.accept(AgentStreamEvent.progress("WORKFLOW_RESOLUTION",
                "正在选择企业预定义的已发布流程并校验本体公理…"));
        var plan = workflowResolver.resolve(intent, ontology);
        events.accept(AgentStreamEvent.progress("PLAN_CREATED",
                "已装载企业流程 %s@%s，共 %d 个固定节点。".formatted(
                        plan.workflowId(), plan.workflowVersion(), plan.nodes().size()), null, plan));
        events.accept(AgentStreamEvent.progress("SCHEMA_VALIDATION", "正在校验能力白名单、数据依赖与副作用审批规则…"));
        validator.validate(plan);
        events.accept(AgentStreamEvent.progress("SCHEMA_VALIDATED", "执行计划已通过确定性 Schema 校验。"));
        events.accept(AgentStreamEvent.progress("GRAPH_COMPILING", "正在编译 Spring AI Alibaba 动态 Graph…"));
        var graph = graphCompiler.compile(plan);
        events.accept(AgentStreamEvent.progress("GRAPH_COMPILED", "动态图编译完成，开始逐节点执行。"));
        return graphRuntime.start(graph, plan, userPrompt, events);
    }

    /** 将人工决策交给运行时恢复或终止已有 execution。 */
    public AgentExecutionResponse decide(String executionId, boolean approved) {
        return graphRuntime.decide(executionId, approved);
    }

    /** 提交人工决策，并继续把恢复后的 Graph 节点进度写入同一 SSE 响应。 */
    public AgentExecutionResponse decide(String executionId, boolean approved, Consumer<AgentStreamEvent> events) {
        return graphRuntime.decide(executionId, approved, events);
    }

    /** 校验并提交当前 Graph 中断点要求的结构化人工资料。 */
    public AgentExecutionResponse submitInteraction(String executionId, String interactionId,
                                                     java.util.Map<String, Object> values,
                                                     Consumer<AgentStreamEvent> events) {
        return graphRuntime.submitInteraction(executionId, interactionId, values, events);
    }

    /** 返回实际动态计划、Mermaid 图和逐节点运行轨迹，用于诊断而不是驱动业务执行。 */
    public ExecutionTraceSnapshot trace(String executionId) {
        return traceStore.get(executionId);
    }

    /** 保留旧调用方式，便于已有集成逐步迁移。 */
    public String handleUserMessage(String userPrompt) {
        return start(userPrompt).content();
    }
}
