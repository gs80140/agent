package com.example.supportagent.controller;

import com.example.supportagent.service.SupportAgentService;
import com.example.supportagent.workflow.AgentExecutionResponse;
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

/** Ontology Agent 的 HTTP API：创建 execution，以及对中断的 execution 提交人工决策。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final SupportAgentService supportAgentService;

    public AgentController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    /** 开始处理用户消息；通常先返回 WAITING_APPROVAL，而不是立即执行写操作。 */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentExecutionResponse executeAgent(@Valid @RequestBody AgentRequest request) {
        return supportAgentService.start(request.prompt());
    }

    /** 批准或拒绝待处理的副作用操作。executionId 同时也是 Graph threadId。 */
    @PostMapping(value = "/executions/{executionId}/decision", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentExecutionResponse decide(
            @org.springframework.web.bind.annotation.PathVariable String executionId,
            @RequestBody ApprovalRequest request) {
        return supportAgentService.decide(executionId, request.approved());
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
}
