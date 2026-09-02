package com.example.supportagent.workflow;

import java.util.List;

/** REST 层统一返回的执行快照，前端依据 status 决定是否显示审批按钮。 */
public record AgentExecutionResponse(
        String executionId,
        Status status,
        String content,
        String ontologyVersion,
        String goal,
        List<String> plannedCapabilities) {

    /** execution 的对外生命周期状态。 */
    public enum Status { WAITING_APPROVAL, COMPLETED, REJECTED, FAILED }
}
