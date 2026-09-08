package com.gaozhaoyang.agent.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 单个 Agent 节点的可持久化调用轨迹，字段设计与分布式 Trace 的 Span 对齐。 */
public record AgentTraceSpan(
        String spanId,
        String traceId,
        String parentSpanId,
        String operation,
        String status,
        Instant startedAt,
        Instant endedAt,
        long durationMs,
        Map<String, String> attributes
) {
    public AgentTraceSpan {
        parentSpanId = parentSpanId == null ? "" : parentSpanId;
        status = status == null ? "UNKNOWN" : status;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        durationMs = Math.max(durationMs, 0);
    }

    public static AgentTraceSpan success(
            String traceId,
            String operation,
            Instant startedAt,
            Map<String, String> attributes
    ) {
        return create(traceId, operation, "SUCCESS", startedAt, attributes);
    }

    public static AgentTraceSpan error(
            String traceId,
            String operation,
            Instant startedAt,
            RuntimeException exception
    ) {
        return create(traceId, operation, "ERROR", startedAt, Map.of(
                "error.type", exception.getClass().getSimpleName()
        ));
    }

    private static AgentTraceSpan create(
            String traceId,
            String operation,
            String status,
            Instant startedAt,
            Map<String, String> attributes
    ) {
        Instant endedAt = Instant.now();
        return new AgentTraceSpan(
                UUID.randomUUID().toString(),
                traceId,
                "",
                operation,
                status,
                startedAt,
                endedAt,
                Duration.between(startedAt, endedAt).toMillis(),
                attributes
        );
    }
}
