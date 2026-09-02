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
        // 重名会由 toUnmodifiableMap 主动报错，避免 Ontology 映射到不确定实现。
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                CapabilityHandler::name, Function.identity()));
    }

    /** 查找实现；动态计划无法通过任意类名或反射绕过这个入口。 */
    public CapabilityHandler require(String name) {
        var handler = handlers.get(name);
        if (handler == null) throw new IllegalArgumentException("未注册的 Capability implementation: " + name);
        return handler;
    }

    public boolean contains(String name) { return handlers.containsKey(name); }
}
