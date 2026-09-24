package com.gaozhaoyang.agent.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowClarificationRequest(
        @NotBlank(message = "补充信息不能为空")
        @Size(max = 4000, message = "补充信息不能超过4000字")
        String clarification
) {
}
