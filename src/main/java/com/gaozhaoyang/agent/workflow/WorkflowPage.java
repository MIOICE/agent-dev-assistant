package com.gaozhaoyang.agent.workflow;

import java.util.List;

public record WorkflowPage(
        List<WorkflowSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
