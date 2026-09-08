package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.stage").value("QUEUED"));
    }
}
