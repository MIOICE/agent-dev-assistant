package com.gaozhaoyang.agent.coding;

public record BuildVerification(
        boolean passed,
        String command,
        int exitCode,
        long durationMs,
        String outputSummary
) {
}
