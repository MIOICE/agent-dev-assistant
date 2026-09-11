package com.gaozhaoyang.agent.tool;

import java.time.Instant;

public record ToolAuditRecord(
        String auditId,
        String traceId,
        String toolName,
        String channel,
        String status,
        int inputChars,
        int outputChars,
        long durationMs,
        Instant occurredAt,
        String errorType
) {
}
