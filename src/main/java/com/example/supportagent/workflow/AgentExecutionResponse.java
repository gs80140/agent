package com.example.supportagent.workflow;

import java.util.List;

/** REST 层统一返回的执行快照，前端依据 status 和 interaction 渲染动态资料表单或审批按钮。 */
public record AgentExecutionResponse(
        String executionId,
        Status status,
        String content,
        String ontologyVersion,
        String goal,
        List<String> plannedCapabilities,
        PendingInteraction interaction) {

    /** execution 的对外生命周期状态。 */
    public enum Status { WAITING_INPUT, WAITING_APPROVAL, COMPLETED, NOT_ELIGIBLE, REJECTED, FAILED }
}
