package com.example.supportagent.service;

import com.example.supportagent.ontology.EnterpriseOntologyRegistry;
import com.example.supportagent.workflow.AgentExecutionResponse;
import com.example.supportagent.workflow.AgentGraphRuntime;
import com.example.supportagent.workflow.DynamicGraphCompiler;
import com.example.supportagent.workflow.ExecutionPlanValidator;
import com.example.supportagent.workflow.OntologyCapabilityPlanner;
import org.springframework.stereotype.Service;

@Service
public class SupportAgentService {

    private final EnterpriseOntologyRegistry ontologyRegistry;
    private final OntologyCapabilityPlanner planner;
    private final ExecutionPlanValidator validator;
    private final DynamicGraphCompiler graphCompiler;
    private final AgentGraphRuntime graphRuntime;

    public SupportAgentService(EnterpriseOntologyRegistry ontologyRegistry, OntologyCapabilityPlanner planner,
                               ExecutionPlanValidator validator, DynamicGraphCompiler graphCompiler,
                               AgentGraphRuntime graphRuntime) {
        this.ontologyRegistry = ontologyRegistry;
        this.planner = planner;
        this.validator = validator;
        this.graphCompiler = graphCompiler;
        this.graphRuntime = graphRuntime;
    }

    public AgentExecutionResponse start(String userPrompt) {
        var ontology = ontologyRegistry.current();
        var plan = planner.plan(userPrompt, ontology);
        validator.validate(plan);
        return graphRuntime.start(graphCompiler.compile(plan), plan, userPrompt);
    }

    public AgentExecutionResponse decide(String executionId, boolean approved) {
        return graphRuntime.decide(executionId, approved);
    }

    /** 保留旧调用方式，便于已有集成逐步迁移。 */
    public String handleUserMessage(String userPrompt) {
        return start(userPrompt).content();
    }
}
