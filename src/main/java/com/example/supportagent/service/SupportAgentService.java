package com.example.supportagent.service;

import com.example.supportagent.ontology.EnterpriseOntologyRegistry;
import com.example.supportagent.workflow.AgentExecutionResponse;
import com.example.supportagent.workflow.AgentGraphRuntime;
import com.example.supportagent.workflow.DynamicGraphCompiler;
import com.example.supportagent.workflow.ExecutionPlanValidator;
import com.example.supportagent.workflow.OntologyCapabilityPlanner;
import com.example.supportagent.workflow.trace.ExecutionTraceSnapshot;
import com.example.supportagent.workflow.trace.ExecutionTraceStore;
import org.springframework.stereotype.Service;

/**
 * 售后 Agent 的应用服务门面。
 *
 * <p>这里只负责串联“固定 Ontology 快照 → 规划 → 校验 → 编译 → 执行”，不包含具体业务规则，
 * 从而避免原来的单体 Service 同时承担提示词、工具选择和流程控制。</p>
 */
@Service
public class SupportAgentService {

    private final EnterpriseOntologyRegistry ontologyRegistry;
    private final OntologyCapabilityPlanner planner;
    private final ExecutionPlanValidator validator;
    private final DynamicGraphCompiler graphCompiler;
    private final AgentGraphRuntime graphRuntime;
    private final ExecutionTraceStore traceStore;

    public SupportAgentService(EnterpriseOntologyRegistry ontologyRegistry, OntologyCapabilityPlanner planner,
                               ExecutionPlanValidator validator, DynamicGraphCompiler graphCompiler,
                               AgentGraphRuntime graphRuntime, ExecutionTraceStore traceStore) {
        this.ontologyRegistry = ontologyRegistry;
        this.planner = planner;
        this.validator = validator;
        this.graphCompiler = graphCompiler;
        this.graphRuntime = graphRuntime;
        this.traceStore = traceStore;
    }

    /** 为一条新的用户诉求创建并启动 execution。 */
    public AgentExecutionResponse start(String userPrompt) {
        // 在请求入口读取一次快照，后续 plan/graph 都固定使用同一个 ontologyVersion。
        var ontology = ontologyRegistry.current();
        var plan = planner.plan(userPrompt, ontology);
        validator.validate(plan);
        return graphRuntime.start(graphCompiler.compile(plan), plan, userPrompt);
    }

    /** 将人工决策交给运行时恢复或终止已有 execution。 */
    public AgentExecutionResponse decide(String executionId, boolean approved) {
        return graphRuntime.decide(executionId, approved);
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
