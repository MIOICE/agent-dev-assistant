package com.gaozhaoyang.agent.workflow;

import java.util.List;

public record WorkflowTraceReport(
        String traceId,
        WorkflowStage stage,
        int spanCount,
        long totalDurationMs,
        long failedSpanCount,
        List<AgentTraceSpan> spans
) {
    public static WorkflowTraceReport from(WorkflowState state) {
        List<AgentTraceSpan> spans = state.traceSpans();
        return new WorkflowTraceReport(
                state.workflowId(),
                state.stage(),
                spans.size(),
                spans.stream().mapToLong(AgentTraceSpan::durationMs).sum(),
                spans.stream().filter(span -> "ERROR".equals(span.status())).count(),
                spans
        );
    }
}
