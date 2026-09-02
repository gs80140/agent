package com.example.supportagent.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.NacosFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * 创建 Nacos AI Prompt 与普通 Config 两套客户端。
 * 两者复用同一 server/namespace，但分别服务于 Prompt 版本管理和 Ontology YAML 推送。
 */
@Configuration
public class NacosAiConfiguration {

    @Bean(destroyMethod = "shutdown")
    AiService nacosAiService(PromptProperties promptProperties) throws NacosException {
        var properties = nacosProperties(promptProperties);
        return AiFactory.createAiService(properties);
    }

    @Bean(destroyMethod = "shutDown")
    ConfigService nacosConfigService(PromptProperties promptProperties) throws NacosException {
        return NacosFactory.createConfigService(nacosProperties(promptProperties));
    }

    private Properties nacosProperties(PromptProperties promptProperties) {
        // 统一构造连接属性，防止两种客户端落到不同 namespace。
        var properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, promptProperties.getServerAddr());
        if (StringUtils.hasText(promptProperties.getNamespaceId())) {
            properties.setProperty(PropertyKeyConst.NAMESPACE, promptProperties.getNamespaceId());
        }
        return properties;
    }
}
