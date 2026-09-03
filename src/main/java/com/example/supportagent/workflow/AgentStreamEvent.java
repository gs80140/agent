package com.example.supportagent.workflow;

import java.time.Instant;

/**
 * Agent SSE 流中的统一事件模型。
 *
 * <p>{@code phase} 是稳定的机器阶段，{@code message} 是直接展示给用户的中文说明，
 * {@code data} 可携带检索结果、计划或最终响应等结构化数据。</p>
 */
public record AgentStreamEvent(
        String phase,
        String message,
        String executionId,
        Instant timestamp,
        Object data) {

    public static AgentStreamEvent progress(String phase, String message) {
        return new AgentStreamEvent(phase, message, null, Instant.now(), null);
    }

    public static AgentStreamEvent progress(String phase, String message, String executionId, Object data) {
        return new AgentStreamEvent(phase, message, executionId, Instant.now(), data);
    }
}
