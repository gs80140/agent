package com.example.supportagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * support-agent.prompt 配置映射。
 * bindings 使用业务别名作为 key，使业务代码不依赖 Nacos 中较长的 Prompt Key。
 */
@Component
@ConfigurationProperties(prefix = "support-agent.prompt")
public class PromptProperties {

    private String serverAddr = "127.0.0.1:8848";
    private String namespaceId = "public";
    private Map<String, Binding> bindings = new LinkedHashMap<>();

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public Map<String, Binding> getBindings() { return bindings; }

    public void setBindings(Map<String, Binding> bindings) { this.bindings = bindings; }

    public static class Binding {
        /** Nacos Prompt Key。 */
        private String key;
        /** 固定订阅某个版本；与 label 互斥。 */
        private String version;
        /** 按业务标签订阅活动版本；与 version 互斥。 */
        private String label;
        /** 必需 Prompt 首次加载不到时是否阻止正常使用。 */
        private boolean required = true;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

}
