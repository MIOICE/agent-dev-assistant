package com.gaozhaoyang.agent.coding;

public record AutonomyBudget(
        int maxFiles,
        long maxTotalBytes,
        int maxBuildExecutions,
        long maxDurationMs
) {
    public static AutonomyBudget safeDefault() {
        return new AutonomyBudget(6, 100_000, 3, 180_000);
    }
}
