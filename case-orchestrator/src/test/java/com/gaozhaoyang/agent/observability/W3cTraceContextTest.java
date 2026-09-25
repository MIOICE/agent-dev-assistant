package com.gaozhaoyang.agent.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class W3cTraceContextTest {

    @Test
    void shouldPropagateTheActiveSampledSpan() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(context.spanId()).thenReturn("0123456789abcdef");
        when(context.sampled()).thenReturn(true);

        assertThat(new W3cTraceContext(tracer).traceparent()).contains(
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    }

    @Test
    void shouldNotFabricateAHeaderWithoutAnActiveSpan() {
        Tracer tracer = mock(Tracer.class);

        assertThat(new W3cTraceContext(tracer).traceparent()).isEmpty();
    }
}
