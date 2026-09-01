package com.example.supportagent.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CapabilityCatalog {
    private final Map<String, CapabilityHandler> handlers;

    public CapabilityCatalog(List<CapabilityHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                CapabilityHandler::name, Function.identity()));
    }

    public CapabilityHandler require(String name) {
        var handler = handlers.get(name);
        if (handler == null) throw new IllegalArgumentException("未注册的 Capability implementation: " + name);
        return handler;
    }

    public boolean contains(String name) { return handlers.containsKey(name); }
}
