package com.example.supportagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
        private String key;
        private String version;
        private String label;
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
