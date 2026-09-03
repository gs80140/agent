package com.example.supportagent.workflow;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按已发布 Workflow 的交互 Schema 校验并规范化人工输入，不信任浏览器提交的数据。 */
@Component
public class InteractionInputValidator {

    public Map<String, Object> validate(PendingInteraction pending, Map<String, Object> values) {
        values = values == null ? Map.of() : values;
        for (String key : values.keySet()) {
            if (!pending.properties().containsKey(key)) throw new IllegalArgumentException("交互包含未定义字段: " + key);
        }
        var normalized = new LinkedHashMap<String, Object>();
        for (var entry : pending.properties().entrySet()) {
            String name = entry.getKey();
            var field = entry.getValue();
            Object value = values.get(name);
            if (pending.required().contains(name) && value == null) {
                throw new IllegalArgumentException("缺少必填资料: " + field.label());
            }
            if (value == null) continue;
            normalized.put(name, validateField(name, field, value));
        }
        return Map.copyOf(normalized);
    }

    private Object validateField(String name, HumanInteraction.InputField field, Object value) {
        if ("string".equals(field.type())) {
            String text = String.valueOf(value).trim();
            if (!StringUtils.hasText(text)) throw new IllegalArgumentException(field.label() + "不能为空");
            if (field.minLength() != null && text.length() < field.minLength())
                throw new IllegalArgumentException(field.label() + "长度不能少于 " + field.minLength());
            if (field.maxLength() != null && text.length() > field.maxLength())
                throw new IllegalArgumentException(field.label() + "长度不能超过 " + field.maxLength());
            return text;
        }
        if ("array".equals(field.type())) {
            if (!(value instanceof List<?> list)) throw new IllegalArgumentException(field.label() + "必须是数组");
            var strings = list.stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText).toList();
            if (field.minItems() != null && strings.size() < field.minItems())
                throw new IllegalArgumentException(field.label() + "至少需要 " + field.minItems() + " 项");
            if (field.maxItems() != null && strings.size() > field.maxItems())
                throw new IllegalArgumentException(field.label() + "最多允许 " + field.maxItems() + " 项");
            return strings;
        }
        throw new IllegalArgumentException("不支持的交互字段类型: " + name + "=" + field.type());
    }
}
