package com.example.supportagent.config;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosPromptRegistryTest {

    @Test
    void subscribesByAliasAndUpdatesFromEvent() throws Exception {
        var properties = new PromptProperties();
        var binding = new PromptProperties.Binding();
        binding.setKey("SupportAgentService_SYSTEM_PROMPT");
        binding.setLabel("production");
        var bindings = new LinkedHashMap<String, PromptProperties.Binding>();
        bindings.put("support-system", binding);
        properties.setBindings(bindings);

        var aiService = mock(AiService.class);
        when(aiService.subscribePrompt(eq(binding.getKey()), isNull(), eq("production"),
                any(AbstractNacosPromptListener.class)))
                .thenReturn(prompt("1.0.1", "md5-1", "初始提示词"));

        var registry = new NacosPromptRegistry(properties, aiService);
        registry.subscribeAll();
        assertThat(registry.get("support-system")).isEqualTo("初始提示词");

        var captor = ArgumentCaptor.forClass(AbstractNacosPromptListener.class);
        verify(aiService).subscribePrompt(eq(binding.getKey()), isNull(), eq("production"), captor.capture());
        captor.getValue().onEvent(new NacosPromptEvent(binding.getKey(),
                prompt("1.0.2", "md5-2", "更新后的提示词")));

        assertThat(registry.get("support-system")).isEqualTo("更新后的提示词");
        assertThat(registry.snapshot("support-system").version()).isEqualTo("1.0.2");
    }

    private Prompt prompt(String version, String md5, String template) {
        var prompt = new Prompt("SupportAgentService_SYSTEM_PROMPT", version, template);
        prompt.setMd5(md5);
        return prompt;
    }
}
