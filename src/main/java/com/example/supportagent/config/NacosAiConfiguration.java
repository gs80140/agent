package com.example.supportagent.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Properties;

@Configuration
public class NacosAiConfiguration {

    @Bean(destroyMethod = "shutdown")
    AiService nacosAiService(PromptProperties promptProperties) throws NacosException {
        var properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, promptProperties.getServerAddr());
        if (StringUtils.hasText(promptProperties.getNamespaceId())) {
            properties.setProperty(PropertyKeyConst.NAMESPACE, promptProperties.getNamespaceId());
        }
        return AiFactory.createAiService(properties);
    }
}
