package com.gaozhaoyang.agent.workflow;

import java.time.Instant;

public record WorkflowSummary(
        String workflowId,
        String title,
        String priority,
        WorkflowStage stage,
        int revision,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowSummary from(WorkflowState state) {
        String title = state.requirementCard() == null
                ? abbreviate(state.requirement())
                : state.requirementCard().title();
        String priority = state.requirementCard() == null ? null : state.requirementCard().priority();
        return new WorkflowSummary(state.workflowId(), title, priority, state.stage(),
                state.revision(), state.createdAt(), state.updatedAt());
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "未命名需求";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40) + "…";
    }
}
