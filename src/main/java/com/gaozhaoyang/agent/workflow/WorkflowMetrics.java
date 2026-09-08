package com.gaozhaoyang.agent.workflow;

import java.time.Instant;

public record WorkflowMetrics(
        long totalWorkflows,
        long analyzedWorkflows,
        double firstPassReadyRate,
        double averageClarificationRounds,
        double recommendationAcceptanceRate,
        double completionRate,
        double failureRate,
        double averageWorkflowDurationMs,
        double averageAgentOperationDurationMs,
        Instant generatedAt
) {
}
