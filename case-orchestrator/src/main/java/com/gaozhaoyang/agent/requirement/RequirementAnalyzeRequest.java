package com.gaozhaoyang.agent.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequirementAnalyzeRequest(
        @NotBlank(message = "需求描述不能为空")
        @Size(max = 4000, message = "需求描述不能超过4000字")
        String content
) {
}
