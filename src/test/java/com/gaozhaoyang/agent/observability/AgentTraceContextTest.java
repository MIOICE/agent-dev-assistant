package com.gaozhaoyang.agent.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTraceContextTest {

    @Test
    void shouldRestoreNestedTraceContext() {
        AgentTraceContext context = new AgentTraceContext();

        String value = context.withinTrace("outer", () -> {
            assertThat(context.currentTraceId()).contains("outer");
            context.withinTrace("inner", () -> {
                assertThat(context.currentTraceId()).contains("inner");
                return null;
            });
            assertThat(context.currentTraceId()).contains("outer");
            return "done";
        });

        assertThat(value).isEqualTo("done");
        assertThat(context.currentTraceId()).isEmpty();
    }

    @Test
    void shouldClearContextWhenOperationFails() {
        AgentTraceContext context = new AgentTraceContext();

        assertThatThrownBy(() -> context.withinTrace("trace-1", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(context.currentTraceId()).isEmpty();
    }
}
