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
        try (var input = resourceLoader.getResource(properties.getFallback()).getInputStream()) {
            replace(yamlMapper.readValue(input, OntologyDefinition.class), "fallback");
        }
    }

    public OntologyDefinition current() {
        return Objects.requireNonNull(current.get(), "Ontology 尚未加载");
    }

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
        OntologyValidator.validateDefinition(definition);
        current.set(definition);
        log.info("Ontology 已更新，version={}, capabilities={}, source={}",
                definition.version(), definition.capabilities().size(), source);
    }
}
