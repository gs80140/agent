package com.example.supportagent.controller;

import com.example.supportagent.service.SupportAgentService;
import com.example.supportagent.workflow.AgentExecutionResponse;
import com.example.supportagent.workflow.AgentStreamEvent;
import com.example.supportagent.workflow.trace.ExecutionTraceSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Consumer;

/** Ontology Agent 的 HTTP API：创建 execution，以及对中断的 execution 提交资料或人工决策。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final SupportAgentService supportAgentService;

    public AgentController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    /** 开始处理用户消息；存在人工节点时返回 WAITING_INPUT 或 WAITING_APPROVAL。 */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentExecutionResponse executeAgent(@Valid @RequestBody AgentRequest request) {
        return supportAgentService.start(request.prompt());
    }

    /**
     * POST SSE 入口。EventSource 只支持 GET，因此 ChatUI 使用 fetch ReadableStream 消费此接口。
     * Agent 在虚拟线程运行，当前 Servlet 线程会立即归还容器。
     */
    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeAgentStream(@Valid @RequestBody AgentRequest request) {
        return stream(events -> supportAgentService.start(request.prompt(), events));
    }

    /** 批准或拒绝待处理的副作用操作。executionId 同时也是 Graph threadId。 */
    @PostMapping(value = "/executions/{executionId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentExecutionResponse decide(
            @org.springframework.web.bind.annotation.PathVariable String executionId,
            @RequestBody ApprovalRequest request) {
        return supportAgentService.decide(executionId, request.approved());
    }

    /** 人工审批后的恢复过程同样使用 SSE，继续展示剩余 Graph 节点。 */
    @PostMapping(value = "/executions/{executionId}/decision/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter decideStream(
            @org.springframework.web.bind.annotation.PathVariable String executionId,
            @RequestBody ApprovalRequest request) {
        return stream(events -> supportAgentService.decide(executionId, request.approved(), events));
    }

    /** 提交当前人工资料交互，并通过 SSE 推送恢复后的节点及下一个中断。 */
    @PostMapping(value = "/executions/{executionId}/interactions/{interactionId}/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter submitInteractionStream(
            @org.springframework.web.bind.annotation.PathVariable String executionId,
            @org.springframework.web.bind.annotation.PathVariable String interactionId,
            @RequestBody InteractionRequest request) {
        return stream(events -> supportAgentService.submitInteraction(executionId, interactionId,
                request.values(), events));
    }

    /** 通用人工资料交互的普通 JSON 入口，便于非流式客户端和接口调试工具使用。 */
    @PostMapping(value = "/executions/{executionId}/interactions/{interactionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentExecutionResponse submitInteraction(
            @org.springframework.web.bind.annotation.PathVariable String executionId,
            @org.springframework.web.bind.annotation.PathVariable String interactionId,
            @RequestBody InteractionRequest request) {
        return supportAgentService.submitInteraction(executionId, interactionId, request.values(), ignored -> { });
    }

    /** 查询动态 Graph 和逐节点执行轨迹；适合 ChatUI、排障页面和 API 调试工具使用。 */
    @GetMapping(value = "/executions/{executionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionTraceSnapshot trace(
            @org.springframework.web.bind.annotation.PathVariable String executionId) {
        return supportAgentService.trace(executionId);
    }

    /** 新建 Agent execution 的请求体。 */
    public record AgentRequest(
            @NotBlank(message = "prompt 不能为空")
            @Size(max = 4000, message = "prompt 不能超过 4000 个字符")
            String prompt) {}

    /** 人工决策请求；false 表示拒绝并终止。 */
    public record ApprovalRequest(boolean approved) {}

    /** FORM_INPUT 等通用人工交互的请求体，字段集合由已发布 Workflow Schema 决定。 */
    public record InteractionRequest(java.util.Map<String, Object> values) {}

    private SseEmitter stream(StreamOperation operation) {
        // 0 表示不采用 MVC 默认超时；连接会在 result/error 发送后由本方法主动关闭。
        var emitter = new SseEmitter(0L);
        Thread.startVirtualThread(() -> {
            Object lock = new Object();
            Consumer<AgentStreamEvent> events = event -> send(emitter, lock, "progress", event);
            try {
                var result = operation.call(events);
                send(emitter, lock, "result", new AgentStreamEvent("RESULT", "本阶段处理完成。",
                        result.executionId(), Instant.now(), result));
                emitter.complete();
            } catch (Exception exception) {
                send(emitter, lock, "error", new AgentStreamEvent("ERROR",
                        exception.getMessage() == null ? "Agent 执行失败" : exception.getMessage(),
                        null, Instant.now(), null));
                emitter.complete();
            }
        });
        return emitter;
    }

    /** SseEmitter.send 不是为并发发送设计的，使用请求级锁保证事件帧不会交错。 */
    private void send(SseEmitter emitter, Object lock, String eventName, AgentStreamEvent event) {
        synchronized (lock) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(event));
            } catch (IOException | IllegalStateException ignored) {
                // 浏览器关闭页面属于正常断连；Agent 仍按服务端业务语义执行到安全边界。
            }
        }
    }

    @FunctionalInterface
    private interface StreamOperation {
        AgentExecutionResponse call(Consumer<AgentStreamEvent> events) throws Exception;
    }
}
