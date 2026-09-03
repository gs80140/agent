package com.example.supportagent.ontology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 当前生效 Ontology 的注册中心。
 *
 * <p>启动时先加载 classpath fallback，保证 Nacos 暂时不可达时服务仍有一份可用知识；
 * 后续 Nacos 推送经过完整解析与校验后，再用原子引用一次性替换快照。</p>
 */
@Component
public class EnterpriseOntologyRegistry {
    private static final Logger log = LoggerFactory.getLogger(EnterpriseOntologyRegistry.class);
    private final OntologyProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final AtomicReference<OntologyDefinition> current = new AtomicReference<>();

    public EnterpriseOntologyRegistry(OntologyProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadFallback() throws IOException {
        // fallback 是启动基线，不依赖外部配置中心，因此加载失败应直接阻止应用启动。
        try (var input = resourceLoader.getResource(properties.getFallback()).getInputStream()) {
            replace(yamlMapper.readValue(input, OntologyDefinition.class), "fallback");
        }
    }

    /** 返回一次请求应当固定使用的不可变 Ontology 快照。 */
    public OntologyDefinition current() {
        return Objects.requireNonNull(current.get(), "Ontology 尚未加载");
    }

    /**
     * 接收 YAML 文本并尝试发布新版本。
     * 解析或校验失败时不会改变 current，调用方可以继续使用最近一次有效版本。
     */
    public synchronized void replaceYaml(String yaml, String source) {
        if (!StringUtils.hasText(yaml)) {
            log.warn("忽略空 Ontology 更新，source={}", source);
            return;
        }
        try {
            replace(yamlMapper.readValue(yaml, OntologyDefinition.class), source);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Ontology YAML 解析失败，source=" + source, exception);
        }
    }

    private void replace(OntologyDefinition definition, String source) {
        // 必须先校验后替换，不能让并发请求观察到半合法状态。
        OntologyValidator.validateDefinition(definition);
        current.set(definition);
        log.info("Ontology 已更新，version={}, C={}, R={}, F={}, A={}, I={}, source={}",
                definition.version(), definition.concepts().size(), definition.relations().size(),
                definition.functions().size(), definition.axioms().size(), definition.instances().size(), source);
    }
}
