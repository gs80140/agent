package com.example.supportagent.workflow.capability;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.supportagent.workflow.CapabilityHandler;
import com.example.supportagent.workflow.CapabilitySchema;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 将原始用户文字转换为后续节点使用的结构化字段。
 * Demo 用规则实现以保证测试确定性；生产环境可替换为 LLM structured output 实现，name 保持不变即可。
 */
@Component
public class ExtractRequestHandler implements CapabilityHandler {
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");

    @Override public CapabilitySchema schema() {
        return new CapabilitySchema("understand-customer-request", "理解客户售后诉求", "extractRequest",
                java.util.List.of("userPrompt"),
                java.util.List.of("userIdentifier", "targetProduct", "serviceType", "reason"),
                CapabilitySchema.EffectLevel.NONE, false);
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // userPrompt 是 Graph 唯一的初始业务输入，其余字段均由 Capability 逐步产生。
        String prompt = state.value("userPrompt", "");
        var phone = PHONE.matcher(prompt);
        // 当前模拟数据只有张三；未识别身份时也回退到张三仅用于演示完整链路。
        String identifier = phone.find() ? phone.group() : prompt.contains("张三") ? "张三" : "张三";
        String product = prompt.contains("耳机") ? "降噪蓝牙耳机" : "无线机械键盘";
        String serviceType = prompt.contains("换货") ? "EXCHANGE" : prompt.contains("维修") ? "REPAIR" : "REFUND";
        String reason = prompt.contains("连击") ? "按键连击失灵" : prompt.contains("杂音") ? "使用时出现杂音" : prompt;
        return Map.of("userIdentifier", identifier, "targetProduct", product,
                "serviceType", serviceType, "reason", reason);
    }
}
