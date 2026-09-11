package com.gaozhaoyang.agent.observability;

import java.time.Instant;
import java.util.List;

public record UnifiedAgentTraceReport(
        String traceId,
        String workflowId,
        String codingTaskId,
        String status,
        Instant startedAt,
        Instant updatedAt,
        AgentRunSummary summary,
        List<UnifiedAgentSpan> spans
) {
}
