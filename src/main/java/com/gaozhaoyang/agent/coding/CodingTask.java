package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;

import java.time.Instant;
import java.util.List;

public record CodingTask(
        String taskId,
        String workflowId,
        WorkflowState workflowSnapshot,
        CodingTaskStage stage,
        String summary,
        AutonomyBudget budget,
        int consumedFiles,
        long consumedBytes,
        int consumedBuildExecutions,
        long consumedDurationMs,
        int repairAttempts,
        List<PatchFile> patches,
        BuildVerification verification,
        List<BuildAttempt> buildAttempts,
        List<CodingTaskEvent> events,
        String workspaceId,
        String approvedOutputPath,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public CodingTask {
        patches = patches == null ? List.of() : List.copyOf(patches);
        buildAttempts = buildAttempts == null ? List.of() : List.copyOf(buildAttempts);
        events = events == null ? List.of() : List.copyOf(events);
        workspaceId = workspaceId == null ? "" : workspaceId;
        approvedOutputPath = approvedOutputPath == null ? "" : approvedOutputPath;
        failureMessage = failureMessage == null ? "" : failureMessage;
    }
}
