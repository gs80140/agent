package com.example.supportagent.ontology;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "support-agent.ontology")
public class OntologyProperties {
    private String dataId = "support-agent-ontology.yaml";
    private String group = "DEFAULT_GROUP";
    private String fallback = "classpath:ontology/support-agent.yaml";
    private long timeoutMs = 3000;

    public String getDataId() { return dataId; }
    public void setDataId(String dataId) { this.dataId = dataId; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getFallback() { return fallback; }
    public void setFallback(String fallback) { this.fallback = fallback; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
