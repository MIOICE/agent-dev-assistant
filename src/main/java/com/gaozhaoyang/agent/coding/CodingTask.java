package com.gaozhaoyang.agent.coding;

import java.time.Instant;
import java.util.List;

public record CodingTask(
        String taskId,
        String workflowId,
        CodingTaskStage stage,
        String summary,
        AutonomyBudget budget,
        int consumedFiles,
        long consumedBytes,
        int consumedBuildExecutions,
        List<PatchFile> patches,
        BuildVerification verification,
        List<CodingTaskEvent> events,
        String approvedOutputPath,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public CodingTask {
        patches = patches == null ? List.of() : List.copyOf(patches);
        events = events == null ? List.of() : List.copyOf(events);
        approvedOutputPath = approvedOutputPath == null ? "" : approvedOutputPath;
        failureMessage = failureMessage == null ? "" : failureMessage;
    }
}
