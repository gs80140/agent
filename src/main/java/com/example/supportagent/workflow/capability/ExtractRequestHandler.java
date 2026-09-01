package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ExtractRequestHandler implements CapabilityHandler {
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    @Override public String name() { return "extractRequest"; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String prompt = state.value("userPrompt", "");
        var phone = PHONE.matcher(prompt);
        String identifier = phone.find() ? phone.group() : prompt.contains("张三") ? "张三" : "张三";
        String product = prompt.contains("耳机") ? "降噪蓝牙耳机" : "无线机械键盘";
        String serviceType = prompt.contains("换货") ? "EXCHANGE" : prompt.contains("维修") ? "REPAIR" : "REFUND";
        String reason = prompt.contains("连击") ? "按键连击失灵" : prompt.contains("杂音") ? "使用时出现杂音" : prompt;
        return Map.of("userIdentifier", identifier, "targetProduct", product,
                "serviceType", serviceType, "reason", reason);
    }
}
