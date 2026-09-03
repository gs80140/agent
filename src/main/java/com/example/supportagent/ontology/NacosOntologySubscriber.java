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

/**
 * 将 Nacos Config 的主动推送桥接到 {@link EnterpriseOntologyRegistry}。
 *
 * <p>这里使用 Listener，而不是定时轮询。Nacos 不可用或新配置不合法时只记录告警，
 * Registry 中的 fallback/最近有效版本保持不变。</p>
 */
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
        // Nacos 回调可能运行在 SDK 线程中，因此 Registry 的替换操作必须是线程安全的。
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
            // 先同步读取当前值，避免注册 Listener 到第一次推送之间存在配置空窗期。
            var current = configService.getConfig(properties.getDataId(), properties.getGroup(),
                    properties.getTimeoutMs());
            if (StringUtils.hasText(current)) {
                try {
                    registry.replaceYaml(current, "nacos:" + properties.getDataId());
                } catch (RuntimeException exception) {
                    // 允许应用先升级数据模型，再单独发布新版 Ontology；迁移期间继续使用 fallback。
                    log.warn("Nacos 当前 Ontology 与五元组模型不兼容，继续使用 classpath fallback，dataId={}, 原因={}",
                            properties.getDataId(), exception.getMessage());
                }
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
        // 应用关闭时解除监听，避免 SDK 持有回调对象和线程资源。
        if (listener != null) {
            configService.removeListener(properties.getDataId(), properties.getGroup(), listener);
        }
    }
}
