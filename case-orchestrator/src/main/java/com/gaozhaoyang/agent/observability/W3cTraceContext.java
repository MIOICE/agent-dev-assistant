package com.gaozhaoyang.agent.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Builds a W3C traceparent header from the active Micrometer/OpenTelemetry span. */
@Component
public class W3cTraceContext {

    private final Tracer tracer;

    public W3cTraceContext(Tracer tracer) {
        this.tracer = tracer;
    }

    public Optional<String> traceparent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return Optional.empty();
        }
        TraceContext context = span.context();
        if (context == null || context.traceId().isBlank() || context.spanId().isBlank()) {
            return Optional.empty();
        }
        String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return Optional.of("00-" + context.traceId() + "-" + context.spanId() + "-" + flags);
    }
}
