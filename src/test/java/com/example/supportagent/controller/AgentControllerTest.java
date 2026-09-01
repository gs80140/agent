package com.example.supportagent.controller;

import com.example.supportagent.service.SupportAgentService;
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
        when(supportAgentService.handleUserMessage(anyString())).thenReturn("工单已创建");

        mockMvc.perform(post("/api/agent/chat")
                        .contentType("application/json")
                        .content("{\"prompt\":\"我是张三，帮我处理键盘退款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("工单已创建"));
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
