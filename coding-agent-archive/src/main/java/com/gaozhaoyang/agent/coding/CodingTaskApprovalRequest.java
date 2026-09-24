package com.gaozhaoyang.agent.coding;

import jakarta.validation.constraints.Size;

public record CodingTaskApprovalRequest(
        @Size(max = 500, message = "审批备注不能超过500字")
        String comment
) {
}
