package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileCodingTaskRepositoryTest {

    @Test
    void shouldPersistAndReloadCheckpointFromDisk() {
        Path root = Path.of(
                ".codex-target", "checkpoint-tests", UUID.randomUUID().toString())
                .toAbsolutePath();
        FileCodingTaskRepository firstProcess =
                new FileCodingTaskRepository(new ObjectMapper(), root.toString());
        Instant now = Instant.now();
        WorkflowState workflowSnapshot = new WorkflowState(
                "workflow-1", "订单列表增加导出功能", List.of(),
                WorkflowStage.COMPLETED, null, List.of(), null, List.of(),
                List.of(), 1, now, now, List.of(), null
        );
        CodingTask checkpoint = new CodingTask(
                "task-1", "workflow-1", workflowSnapshot, CodingTaskStage.VERIFYING,
                "代码已生成", AutonomyBudget.safeDefault(),
                2, 1200, 1, 25, 0,
                List.of("java-code-generation"),
                List.of(), null, List.of(),
                List.of(CodingTaskEvent.of("BUILD_STARTED", "开始测试")),
                "workspace-1", "", "", now, now
        );

        firstProcess.save(checkpoint);
        FileCodingTaskRepository restartedProcess =
                new FileCodingTaskRepository(new ObjectMapper(), root.toString());

        assertThat(restartedProcess.findById("task-1"))
                .contains(checkpoint);
        assertThat(restartedProcess.findLatestByWorkflowId("workflow-1"))
                .contains(checkpoint);
        assertThat(restartedProcess.findByStages(Set.of(CodingTaskStage.VERIFYING)))
                .containsExactly(checkpoint);
    }
}
