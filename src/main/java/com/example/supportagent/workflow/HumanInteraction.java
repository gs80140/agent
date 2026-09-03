package com.example.supportagent.workflow;

import java.util.List;
import java.util.Map;

/** 企业 Workflow 定义的人工交互契约，前端据此渲染表单，后端据此确定性校验输入。 */
public record HumanInteraction(
        Type type,
        String title,
        String description,
        List<String> required,
        Map<String, InputField> properties) {

    public HumanInteraction {
        required = required == null ? List.of() : List.copyOf(required);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public enum Type { APPROVAL, FORM_INPUT, FILE_UPLOAD, CLARIFICATION, SELECTION }

    public record InputField(String type, String label, Integer minLength, Integer maxLength,
                             Integer minItems, Integer maxItems) {}
}
