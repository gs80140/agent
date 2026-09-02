package com.example.supportagent.config;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多 Prompt 的线程安全订阅注册中心。
 *
 * <p>每个 binding 对应一个独立 Listener。事件为空通常表示当前订阅目标下架，此时不会把
 * 有效 Prompt 覆盖成空值，而是继续使用最近一次有效快照。</p>
 */
@Component
public class NacosPromptRegistry {
    private static final Logger log = LoggerFactory.getLogger(NacosPromptRegistry.class);
    private final PromptProperties properties;
    private final AiService aiService;
    private final Map<String, PromptSnapshot> prompts = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public NacosPromptRegistry(PromptProperties properties, AiService aiService) {
        this.properties = properties;
        this.aiService = aiService;
    }

    @PostConstruct
    void subscribeAll() {
        // 配置为空通常是部署错误，启动阶段尽早暴露比运行时随机失败更清晰。
        if (properties.getBindings().isEmpty()) {
            throw new IllegalStateException("未配置任何 Nacos Prompt 绑定");
        }
        properties.getBindings().forEach(this::subscribe);
    }

    /** 按业务别名获取当前模板；从未成功加载时快速失败。 */
    public String get(String name) {
        var snapshot = prompts.get(name);
        if (snapshot == null || !StringUtils.hasText(snapshot.template())) {
            throw new IllegalStateException("Prompt 尚未加载或当前不可用，name=" + name);
        }
        return snapshot.template();
    }

    public PromptSnapshot snapshot(String name) { return prompts.get(name); }

    private void subscribe(String name, PromptProperties.Binding binding) {
        validateBinding(name, binding);
        var listener = new AbstractNacosPromptListener() {
            @Override
            public void onEvent(NacosPromptEvent event) {
                if (event == null || event.getPrompt() == null) handleUnavailable(name, binding);
                else update(name, event.getPrompt());
            }
        };
        try {
            // subscribePrompt 同时完成首次读取和后续事件订阅，可避免轮询刷新。
            var current = aiService.subscribePrompt(binding.getKey(), emptyToNull(binding.getVersion()),
                    emptyToNull(binding.getLabel()), listener);
            subscriptions.put(name, new Subscription(binding, listener));
            if (current == null || !StringUtils.hasText(current.getTemplate())) {
                if (binding.isRequired()) {
                    throw new IllegalStateException("Nacos 未返回必需的 Prompt，name=" + name + ", key=" + binding.getKey());
                }
                log.warn("可选 Prompt 当前不可用，name={}, key={}", name, binding.getKey());
            } else update(name, current);
        } catch (NacosException exception) {
            throw new IllegalStateException("订阅 Nacos Prompt 失败，name=" + name + ", key=" + binding.getKey(), exception);
        }
    }

    private void update(String name, Prompt prompt) {
        var previous = prompts.get(name);
        // md5 未变化时跳过替换和日志，降低重复推送噪声。
        if (previous != null && Objects.equals(previous.md5(), prompt.getMd5())) return;
        var snapshot = new PromptSnapshot(prompt.getPromptKey(), prompt.getVersion(), prompt.getMd5(), prompt.getTemplate());
        prompts.put(name, snapshot);
        log.info("已更新 Nacos Prompt，name={}, key={}, version={}, md5={}",
                name, snapshot.promptKey(), snapshot.version(), snapshot.md5());
    }

    private void handleUnavailable(String name, PromptProperties.Binding binding) {
        // 下架当前版本时 Nacos 可能发送空事件；保留 last-known-good 是有意的容灾策略。
        if (prompts.containsKey(name)) {
            log.warn("Nacos Prompt 订阅目标当前不可用，继续使用最近有效版本，name={}, key={}", name, binding.getKey());
        } else if (binding.isRequired()) {
            log.error("必需的 Nacos Prompt 当前不可用，name={}, key={}", name, binding.getKey());
        } else log.warn("可选的 Nacos Prompt 当前不可用，name={}, key={}", name, binding.getKey());
    }

    private void validateBinding(String name, PromptProperties.Binding binding) {
        if (!StringUtils.hasText(name) || binding == null || !StringUtils.hasText(binding.getKey())) {
            throw new IllegalArgumentException("Prompt 绑定的 name 和 key 不能为空");
        }
        if (StringUtils.hasText(binding.getVersion()) && StringUtils.hasText(binding.getLabel())) {
            // SDK 的 version/label 是两种不同选择器，同时提供会导致订阅语义不明确。
            throw new IllegalArgumentException("Prompt 的 version 与 label 不能同时配置，name=" + name);
        }
    }

    private String emptyToNull(String value) { return StringUtils.hasText(value) ? value : null; }

    @PreDestroy
    void unsubscribeAll() {
        // 保存原始 listener 实例，取消订阅时必须传回同一个对象。
        subscriptions.forEach((name, subscription) -> {
            var binding = subscription.binding();
            try {
                aiService.unsubscribePrompt(binding.getKey(), emptyToNull(binding.getVersion()),
                        emptyToNull(binding.getLabel()), subscription.listener());
            } catch (NacosException exception) {
                log.warn("取消 Nacos Prompt 订阅失败，name={}, key={}", name, binding.getKey(), exception);
            }
        });
        subscriptions.clear();
    }

    /** 对外只暴露不可变快照，避免调用方修改注册中心状态。 */
    public record PromptSnapshot(String promptKey, String version, String md5, String template) {}
    private record Subscription(PromptProperties.Binding binding, AbstractNacosPromptListener listener) {}
}
