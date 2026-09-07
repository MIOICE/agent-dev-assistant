package com.gaozhaoyang.agent.workflow;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlWorkflowRepositoryTest {

    @Test
    void shouldInsertAndUpdateCompleteWorkflowSnapshot() {
        String databaseName = "workflow_" + UUID.randomUUID().toString().replace("-", "");
        MysqlWorkflowRepository repository = new MysqlWorkflowRepository(
                new ObjectMapper(),
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        RequirementCard incompleteCard = new RequirementCard(
                "增加导出功能",
                "增加导出功能",
                List.of(),
                List.of("能够触发导出操作"),
                List.of("需要导出哪个业务对象？"),
                "P2",
                List.of(),
                List.of(),
                false
        );
        WorkflowState waitingState = WorkflowState.start("增加导出功能")
                .completeRequirementAnalysis(incompleteCard)
                .waitForClarification();

        repository.save(waitingState);
        WorkflowState restoredWaitingState = repository
                .findById(waitingState.workflowId())
                .orElseThrow();

        assertThat(restoredWaitingState).isEqualTo(waitingState);

        WorkflowState resumedState = waitingState.addClarification(
                "导出订单列表当前筛选结果，文件格式为CSV"
        );
        repository.save(resumedState);

        assertThat(repository.findById(waitingState.workflowId()))
                .contains(resumedState);
        assertThat(repository.count(null)).isEqualTo(1);
        assertThat(repository.count(WorkflowStage.REQUIREMENT_ANALYSIS)).isEqualTo(1);
        assertThat(repository.findAll(null, 0, 10)).containsExactly(resumedState);
    }

    @Test
    void shouldReadLegacySnapshotWithoutTimelineFields() throws Exception {
        String databaseName = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        MysqlWorkflowRepository repository = new MysqlWorkflowRepository(
                new ObjectMapper(), url, "sa", ""
        );
        String workflowId = UUID.randomUUID().toString();
        String legacyJson = """
                {"workflowId":"%s","requirement":"历史需求","clarifications":[],
                "stage":"REQUIREMENT_ANALYSIS","requirementCard":null,
                "knowledgeResults":[],"technicalSolution":null}
                """.formatted(workflowId).replace("\n", "");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.prepareStatement("""
                     INSERT INTO workflow_state(workflow_id, stage, state_json)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setString(1, workflowId);
            statement.setString(2, WorkflowStage.REQUIREMENT_ANALYSIS.name());
            statement.setString(3, legacyJson);
            statement.executeUpdate();
        }

        WorkflowState restored = repository.findById(workflowId).orElseThrow();

        assertThat(restored.requirement()).isEqualTo("历史需求");
        assertThat(restored.revision()).isEqualTo(1);
        assertThat(restored.events()).singleElement()
                .extracting(WorkflowEvent::message)
                .isEqualTo("历史工作流已兼容迁移");
    }
}
