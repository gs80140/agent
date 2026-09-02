package com.example.supportagent.workflow;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.example.supportagent.workflow.trace.ExecutionTraceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/** 将通过校验的 ExecutionPlan 动态翻译成 Spring AI Alibaba StateGraph。 */
@Component
public class DynamicGraphCompiler {
    private static final Logger log = LoggerFactory.getLogger(DynamicGraphCompiler.class);
    private final CapabilityCatalog catalog;
    private final ExecutionTraceStore traceStore;

    public DynamicGraphCompiler(CapabilityCatalog catalog, ExecutionTraceStore traceStore) {
        this.catalog = catalog;
        this.traceStore = traceStore;
    }

    /**
     * 为一次 execution 创建独立的 CompiledGraph 和 MemorySaver。
     * 独立实例确保不同用户的 checkpoint、threadId 和 Ontology 版本互不污染。
     */
    public CompiledGraph compile(ExecutionPlan plan) {
        try {
            var graph = new StateGraph();
            for (var node : plan.nodes()) {
                var handler = catalog.require(node.implementation());
                // 包一层追踪代理：业务 Handler 无需感知监控，所有动态节点都能得到一致的日志和轨迹。
                graph.addNode(node.id(), node_async(state -> {
                    String executionId = state.value("executionId", "unknown");
                    traceStore.nodeStarted(executionId, node, state.data().keySet().stream().sorted().toList());
                    long started = System.nanoTime();
                    log.info("Graph 节点开始，executionId={}, node={}, capability={}, implementation={}, stateKeys={}",
                            executionId, node.id(), node.capability(), node.implementation(), state.data().keySet());
                    try {
                        var output = handler.apply(state);
                        traceStore.nodeCompleted(executionId, node.id(), output);
                        log.info("Graph 节点完成，executionId={}, node={}, elapsedMs={}, outputKeys={}",
                                executionId, node.id(), (System.nanoTime() - started) / 1_000_000, output.keySet());
                        return output;
                    } catch (Exception exception) {
                        traceStore.nodeFailed(executionId, node.id(), exception);
                        log.error("Graph 节点失败，executionId={}, node={}, capability={}",
                                executionId, node.id(), node.capability(), exception);
                        throw exception;
                    }
                }));
            }
            graph.addEdge(START, plan.nodes().getFirst().id());
            for (int i = 0; i < plan.nodes().size() - 1; i++) {
                graph.addEdge(plan.nodes().get(i).id(), plan.nodes().get(i + 1).id());
            }
            graph.addEdge(plan.nodes().getLast().id(), END);

            String[] approvalNodes = plan.nodes().stream().filter(ExecutionPlan.PlanNode::approvalRequired)
                    .map(ExecutionPlan.PlanNode::id).toArray(String[]::new);
            var compileConfig = CompileConfig.builder()
                    // interruptBefore 会在审批节点执行前保存状态；此时所有写操作均尚未发生。
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
