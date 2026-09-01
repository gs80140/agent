package com.example.supportagent.service;

import com.example.supportagent.config.NacosPromptRegistry;
import com.example.supportagent.tools.CustomerSupportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SupportAgentService {

    private static final String SYSTEM_PROMPT_NAME = "support-system";
    private final ChatClient chatClient;
    private final NacosPromptRegistry promptRegistry;

    public SupportAgentService(ChatClient.Builder builder, CustomerSupportTools tools,
                               NacosPromptRegistry promptRegistry) {
        this.promptRegistry = promptRegistry;
        this.chatClient = builder
                .defaultTools(tools)
                .build();
    }

    public String handleUserMessage(String userPrompt) {
        return chatClient.prompt()
                .system(promptRegistry.get(SYSTEM_PROMPT_NAME))
                .user(userPrompt)
                .call()
                .content();
    }
}
