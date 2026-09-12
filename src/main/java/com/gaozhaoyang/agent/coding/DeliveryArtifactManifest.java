package com.gaozhaoyang.agent.coding;

import java.util.List;

public record DeliveryArtifactManifest(
        String schemaVersion,
        String taskId,
        String workflowId,
        int workflowRevision,
        String publishedAt,
        String summary,
        Verification verification,
        AutonomyBudget autonomyBudget,
        int consumedAgentSteps,
        int consumedBuildExecutions,
        int repairAttempts,
        List<String> activatedSkills,
        List<FileEntry> files
) {
    public DeliveryArtifactManifest {
        activatedSkills = activatedSkills == null ? List.of() : List.copyOf(activatedSkills);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public record Verification(
            boolean passed,
            String command,
            int exitCode,
            long durationMs,
            String outputSummarySha256
    ) {
    }

    public record FileEntry(
            String path,
            String operation,
            long bytes,
            String sha256
    ) {
    }
}
