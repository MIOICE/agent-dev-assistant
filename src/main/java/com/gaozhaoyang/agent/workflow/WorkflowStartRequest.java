package com.gaozhaoyang.agent.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowStartRequest(
    @NotBlank(message = "需求内容不能为空")
    @Size(max = 4000, message = "需求内容不能超过4000字")
    String requirement
) {
} 
    

