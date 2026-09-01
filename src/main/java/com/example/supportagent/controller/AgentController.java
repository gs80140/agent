package com.example.supportagent.controller;

import com.example.supportagent.service.SupportAgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final SupportAgentService supportAgentService;

    public AgentController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentResponse executeAgent(@Valid @RequestBody AgentRequest request) {
        return new AgentResponse(supportAgentService.handleUserMessage(request.prompt()));
    }

    public record AgentRequest(
            @NotBlank(message = "prompt 不能为空")
            @Size(max = 4000, message = "prompt 不能超过 4000 个字符")
            String prompt) {}

    public record AgentResponse(String content) {}
}
