package com.example.supportagent.ontology;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.Executor;

@Component
public class NacosOntologySubscriber {
    private static final Logger log = LoggerFactory.getLogger(NacosOntologySubscriber.class);
    private final ConfigService configService;
    private final OntologyProperties properties;
    private final EnterpriseOntologyRegistry registry;
    private Listener listener;

    public NacosOntologySubscriber(ConfigService configService, OntologyProperties properties,
                                   EnterpriseOntologyRegistry registry) {
        this.configService = configService;
        this.properties = properties;
        this.registry = registry;
    }

    @PostConstruct
    void subscribe() {
        listener = new Listener() {
            @Override public Executor getExecutor() { return null; }
            @Override public void receiveConfigInfo(String configInfo) {
                try {
                    registry.replaceYaml(configInfo, "nacos:" + properties.getDataId());
                } catch (RuntimeException exception) {
                    log.error("Nacos Ontology 更新无效，继续使用最近有效版本，dataId={}",
                            properties.getDataId(), exception);
                }
            }
        };
        try {
            var current = configService.getConfig(properties.getDataId(), properties.getGroup(),
                    properties.getTimeoutMs());
            if (StringUtils.hasText(current)) {
                registry.replaceYaml(current, "nacos:" + properties.getDataId());
            } else {
                log.warn("Nacos 中未配置 Ontology，当前使用 classpath fallback，dataId={}", properties.getDataId());
            }
            configService.addListener(properties.getDataId(), properties.getGroup(), listener);
        } catch (NacosException exception) {
            log.warn("订阅 Nacos Ontology 失败，当前使用 classpath fallback，dataId={}",
                    properties.getDataId(), exception);
        }
    }

    @PreDestroy
    void unsubscribe() {
        if (listener != null) {
            configService.removeListener(properties.getDataId(), properties.getGroup(), listener);
        }
    }
}
