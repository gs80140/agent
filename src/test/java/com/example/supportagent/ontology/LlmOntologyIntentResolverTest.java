package com.example.supportagent.ontology;

import com.example.supportagent.config.NacosPromptRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证常见企业表达走本地快速路径，不依赖模型响应质量和网络延迟。 */
class LlmOntologyIntentResolverTest {

    @Test
    void resolvesExchangeLocallyWithoutCallingLlm() throws Exception {
        OntologyDefinition ontology;
        try (var input = getClass().getResourceAsStream("/ontology/support-agent.yaml")) {
            ontology = new ObjectMapper(new YAMLFactory()).readValue(input, OntologyDefinition.class);
        }
        var builder = mock(ChatClient.Builder.class);
        var chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);
        var resolver = new LlmOntologyIntentResolver(builder, mock(NacosPromptRegistry.class));

        var result = resolver.resolve("我是张三，之前买的蓝牙耳机有杂音，可以换货吗？", ontology);

        assertThat(result.intentId()).isEqualTo("exchange-intent");
        assertThat(result.reasoning()).contains("本地快速路径");
        verifyNoInteractions(chatClient);
    }
}
