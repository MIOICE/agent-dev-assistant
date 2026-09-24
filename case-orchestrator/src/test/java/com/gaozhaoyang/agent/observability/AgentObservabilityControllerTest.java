package com.gaozhaoyang.agent.observability;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentObservabilityControllerTest {

    @Test
    void shouldExposeUnifiedTraceEndpoint() throws Exception {
        AgentObservabilityService service = mock(AgentObservabilityService.class);
        UnifiedAgentTraceReport expected = new UnifiedAgentTraceReport(
                "workflow-1", "workflow-1", "", "RUNNING",
                Instant.now(), Instant.now(),
                new AgentRunSummary(0, 0, 0, 0, 0, 0, 0,
                        0, false, 0, 0, "不可用"),
                List.of());
        when(service.trace("workflow-1")).thenReturn(expected);
        AgentObservabilityController controller = new AgentObservabilityController(service);

        assertThat(controller.trace("workflow-1")).isSameAs(expected);
        assertThat(AgentObservabilityController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/observability");
        Method method = AgentObservabilityController.class
                .getMethod("trace", String.class);
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/traces/{workflowId}");
    }
}
