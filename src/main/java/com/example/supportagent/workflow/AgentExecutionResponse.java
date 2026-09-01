package com.example.supportagent.workflow;

import java.util.List;

public record AgentExecutionResponse(
        String executionId,
        Status status,
        String content,
        String ontologyVersion,
        String goal,
        List<String> plannedCapabilities) {

    public enum Status { WAITING_APPROVAL, COMPLETED, REJECTED, FAILED }
}
