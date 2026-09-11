package com.gaozhaoyang.agent.coding;

public record AutonomyBudget(
        int maxFiles,
        long maxTotalBytes,
        int maxBuildExecutions,
        long maxDurationMs,
        int maxAgentSteps
) {
    private static final int DEFAULT_MAX_AGENT_STEPS = 8;

    public AutonomyBudget {
        if (maxAgentSteps <= 0) {
            maxAgentSteps = DEFAULT_MAX_AGENT_STEPS;
        }
    }

    public AutonomyBudget(
            int maxFiles,
            long maxTotalBytes,
            int maxBuildExecutions,
            long maxDurationMs
    ) {
        this(maxFiles, maxTotalBytes, maxBuildExecutions, maxDurationMs,
                DEFAULT_MAX_AGENT_STEPS);
    }

    public static AutonomyBudget safeDefault() {
        return new AutonomyBudget(6, 100_000, 3, 180_000,
                DEFAULT_MAX_AGENT_STEPS);
    }
}
