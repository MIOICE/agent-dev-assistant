package com.gaozhaoyang.agent.observability;

import java.time.Instant;
import java.util.Map;

/** OpenTelemetry 风格的项目内统一 Span 投影。 */
public record UnifiedAgentSpan(
        String spanId,
        String traceId,
        String parentSpanId,
        String category,
        String operation,
        String status,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        Map<String, String> attributes
) {
    public UnifiedAgentSpan {
        parentSpanId = parentSpanId == null ? "" : parentSpanId;
        category = category == null ? "OTHER" : category;
        status = status == null ? "UNKNOWN" : status;
        durationMs = Math.max(durationMs, 0);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
