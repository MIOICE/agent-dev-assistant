package com.gaozhaoyang.agent.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowRejectionRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 1000, message = "驳回原因不能超过1000个字符")
        String feedback
) {
}
