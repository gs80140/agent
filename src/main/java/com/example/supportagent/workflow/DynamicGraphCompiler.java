package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class DynamicGraphCompiler {
    private final CapabilityCatalog catalog;

    public DynamicGraphCompiler(CapabilityCatalog catalog) { this.catalog = catalog; }

    public CompiledGraph compile(ExecutionPlan plan) {
        try {
            var graph = new StateGraph();
            for (var node : plan.nodes()) {
                graph.addNode(node.id(), node_async(catalog.require(node.implementation())));
            }
            graph.addEdge(START, plan.nodes().getFirst().id());
            for (int i = 0; i < plan.nodes().size() - 1; i++) {
                graph.addEdge(plan.nodes().get(i).id(), plan.nodes().get(i + 1).id());
            }
            graph.addEdge(plan.nodes().getLast().id(), END);

            String[] approvalNodes = plan.nodes().stream().filter(ExecutionPlan.PlanNode::approvalRequired)
                    .map(ExecutionPlan.PlanNode::id).toArray(String[]::new);
            var compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                    .interruptBefore(approvalNodes)
                    .recursionLimit(50)
                    .build();
            return graph.compile(compileConfig);
        } catch (GraphStateException exception) {
            throw new IllegalArgumentException("动态 Graph 编译失败", exception);
        }
    }
}
