package com.gaozhaoyang.agent.observability;

public record AgentRunSummary(
        int spanCount,
        int failedSpanCount,
        int agentOperations,
        int ragOperations,
        int toolCalls,
        int agentLoopSteps,
        int buildExecutions,
        long totalDurationMs,
        boolean tokenUsageAvailable,
        long inputTokens,
        long outputTokens,
        String tokenUsageNote
) {
}
