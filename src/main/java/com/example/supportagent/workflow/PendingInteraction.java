package com.example.supportagent.workflow;

import java.time.Instant;

/** 一次 execution 当前等待的人工交互；interactionId 防止旧页面或重复提交恢复错误节点。 */
public record PendingInteraction(
        String interactionId,
        String nodeId,
        HumanInteraction.Type type,
        String title,
        String description,
        java.util.List<String> required,
        java.util.Map<String, HumanInteraction.InputField> properties,
        Instant expiresAt) {}
