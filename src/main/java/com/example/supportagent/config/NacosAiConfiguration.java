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
        var properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, promptProperties.getServerAddr());
        if (StringUtils.hasText(promptProperties.getNamespaceId())) {
            properties.setProperty(PropertyKeyConst.NAMESPACE, promptProperties.getNamespaceId());
        }
        return properties;
    }
}
