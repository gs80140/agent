package com.example.supportagent.controller;

import com.example.supportagent.service.SupportAgentService;
import com.example.supportagent.workflow.AgentExecutionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                "2026.09.01", "AfterSaleCompleted", java.util.List.of("QueryUserOrders")));

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
}
