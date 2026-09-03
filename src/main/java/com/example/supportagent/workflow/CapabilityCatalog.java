package com.example.supportagent.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 启动时收集所有 CapabilityHandler，形成不可变的受控实现白名单。 */
@Component
public class CapabilityCatalog {
    private final Map<String, CapabilityHandler> handlers;

    public CapabilityCatalog(List<CapabilityHandler> handlers) {
        // 重名会由 toUnmodifiableMap 主动报错，避免企业 Workflow 映射到不确定实现。
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                handler -> handler.schema().id(), Function.identity()));
    }

    /** 查找实现；动态计划无法通过任意类名或反射绕过这个入口。 */
    public CapabilityHandler require(String capabilityId) {
        var handler = handlers.get(capabilityId);
        if (handler == null) throw new IllegalArgumentException("未注册的 Capability ID: " + capabilityId);
        return handler;
    }

    public boolean contains(String capabilityId) { return handlers.containsKey(capabilityId); }

    public List<CapabilitySchema> schemas() {
        return handlers.values().stream().map(CapabilityHandler::schema).toList();
    }
}
