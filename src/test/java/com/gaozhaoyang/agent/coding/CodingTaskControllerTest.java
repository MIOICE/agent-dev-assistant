package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CodingTaskControllerTest {

    @Test
    void shouldAcceptBackgroundTaskWithoutWaitingForCompletion() throws Exception {
        CodingTaskService service = mock(CodingTaskService.class);
        Instant now = Instant.now();
        CodingTask queued = new CodingTask(
                "task-1", "workflow-1", null, CodingTaskStage.QUEUED, "",
                AutonomyBudget.safeDefault(), 0, 0, 0, 0, 0,
                List.of(),
                List.of(), null, List.of(), List.of(),
                "", "", "", now, now
        );
        when(service.submit("workflow-1")).thenReturn(queued);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CodingTaskController(service))
                .build();

        mockMvc.perform(post("/api/coding-tasks")
                        .param("workflowId", "workflow-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.stage").value("QUEUED"))
                .andExpect(jsonPath("$.budget.maxAgentSteps").value(8))
                .andExpect(jsonPath("$.agentLoop.maxSteps").value(8))
                .andExpect(jsonPath("$.agentLoop.consumedSteps").value(0))
                .andExpect(jsonPath("$.agentLoop.stopCode").value("NONE"));
    }

    @Test
    void shouldExposeCodingTaskAsServerSentEventStream() throws Exception {
        CodingTaskService service = mock(CodingTaskService.class);
        SseEmitter emitter = new SseEmitter();
        when(service.stream("task-1")).thenReturn(emitter);
        CodingTaskController controller = new CodingTaskController(service);

        assertThat(controller.stream("task-1")).isSameAs(emitter);
        GetMapping mapping = CodingTaskController.class
                .getMethod("stream", String.class)
                .getAnnotation(GetMapping.class);
        assertThat(mapping.value()).containsExactly("/{taskId}/stream");
        assertThat(mapping.produces()).containsExactly(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void shouldCancelTaskThroughControlEndpoint() throws Exception {
        CodingTaskService service = mock(CodingTaskService.class);
        Instant now = Instant.now();
        CodingTask cancelled = new CodingTask(
                "task-1", "workflow-1", null, CodingTaskStage.CANCELLED, "",
                AutonomyBudget.safeDefault(), 0, 0, 0, 0, 0,
                List.of(), List.of(), null, List.of(), List.of(),
                "", "", "用户取消代码任务：需求撤回", now, now
        );
        when(service.cancel("task-1", "需求撤回")).thenReturn(cancelled);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CodingTaskController(service))
                .build();

        mockMvc.perform(post("/api/coding-tasks/task-1/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"需求撤回\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CANCELLED"))
                .andExpect(jsonPath("$.failureMessage").value("用户取消代码任务：需求撤回"));
    }

    @Test
    void shouldExposeExecutorRuntimeStatus() throws Exception {
        CodingTaskService service = mock(CodingTaskService.class);
        when(service.runtimeStatus()).thenReturn(
                new CodingTaskRuntimeStatus(2, 1, 2, 3, 17, 1, 300_000));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CodingTaskController(service))
                .build();

        mockMvc.perform(get("/api/coding-tasks/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corePoolSize").value(2))
                .andExpect(jsonPath("$.queuedTasks").value(3))
                .andExpect(jsonPath("$.maxTaskRuntimeMs").value(300000));
    }
}
