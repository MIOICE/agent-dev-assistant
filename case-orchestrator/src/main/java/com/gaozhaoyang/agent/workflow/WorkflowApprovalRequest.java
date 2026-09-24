package com.gaozhaoyang.agent.workflow;

import jakarta.validation.constraints.Size;

public record WorkflowApprovalRequest(
        @Size(max = 1000, message = "审批备注不能超过1000个字符")
        String comment
) {
}
