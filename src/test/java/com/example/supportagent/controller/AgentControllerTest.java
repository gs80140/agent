package com.example.supportagent.controller;

import com.example.supportagent.service.SupportAgentService;
import com.example.supportagent.workflow.AgentExecutionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportAgentService supportAgentService;

    @Test
    void returnsAgentResponseAsJson() throws Exception {
        when(supportAgentService.start(anyString())).thenReturn(new AgentExecutionResponse(
                "exec-1", AgentExecutionResponse.Status.WAITING_APPROVAL, "工单待确认",
                "2026.09.01", "AfterSaleCompleted", java.util.List.of("QueryUserOrders"), null));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType("application/json")
                        .content("{\"prompt\":\"我是张三，帮我处理键盘退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("工单待确认"))
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.executionId").value("exec-1"));
    }

    @Test
    void rejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType("application/json")
                        .content("{\"prompt\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("prompt 不能为空"));
    }

    @Test
    void streamsProgressAndResultAsSse() throws Exception {
        var result = new AgentExecutionResponse("exec-sse", AgentExecutionResponse.Status.WAITING_APPROVAL,
                "等待确认", "2026.09.02", "处理退款", java.util.List.of("查询订单"), null);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var events = (java.util.function.Consumer<com.example.supportagent.workflow.AgentStreamEvent>)
                    invocation.getArgument(1);
            events.accept(com.example.supportagent.workflow.AgentStreamEvent.progress(
                    "KNOWLEDGE_RETRIEVAL", "正在检索企业知识…"));
            return result;
        }).when(supportAgentService).start(anyString(), any());

        var async = mockMvc.perform(post("/api/agent/chat/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"prompt\":\"帮我处理键盘退款\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Controller 在 Java 21 虚拟线程中发送 SSE；显式等待完成，避免测试线程抢先 asyncDispatch。
        async.getAsyncResult(5000);
        mockMvc.perform(asyncDispatch(async))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KNOWLEDGE_RETRIEVAL")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:result")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("exec-sse")));
    }

    @Test
    void submitsWorkflowInteractionAsJson() throws Exception {
        var result = new AgentExecutionResponse("exec-input", AgentExecutionResponse.Status.WAITING_APPROVAL,
                "资料已核验，等待批准", "2026.09.04", "处理退款", java.util.List.of("核验资料"), null);
        when(supportAgentService.submitInteraction(eq("exec-input"), eq("interaction-1"), anyMap(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/api/agent/executions/exec-input/interactions/interaction-1")
                        .contentType("application/json")
                        .content("""
                                {"values":{"problemDescription":"按键一次会连续输入三次",
                                "evidenceUrls":["https://example.test/evidence.jpg"]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.executionId").value("exec-input"));
    }

    @Test
    void ignoresChromeDevToolsWellKnownProbe() throws Exception {
        mockMvc.perform(get("/.well-known/appspecific/com.chrome.devtools.json"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void returnsNotFoundForOtherMissingStaticResources() throws Exception {
        mockMvc.perform(get("/missing-resource.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("资源未找到"))
                .andExpect(jsonPath("$.detail").value("请求的资源不存在"));
    }
}
