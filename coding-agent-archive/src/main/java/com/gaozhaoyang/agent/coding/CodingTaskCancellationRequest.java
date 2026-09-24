package com.gaozhaoyang.agent.coding;

import jakarta.validation.constraints.Size;

public record CodingTaskCancellationRequest(
        @Size(max = 500, message = "取消原因不能超过500个字符")
        String reason
) {
}
