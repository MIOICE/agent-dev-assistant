package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.RequirementWorkflowService;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingTaskServiceTest {

    @Test
    void shouldGenerateVerifyAndPublishOnlyAfterApproval() {
        Path root = testRoot();
        CodingTaskService service = service(root, true, WorkflowStage.COMPLETED);

        CodingTask generated = service.create("workflow-1");

        assertThat(generated.stage()).isEqualTo(CodingTaskStage.WAITING_APPROVAL);
        assertThat(generated.patches()).hasSize(2);
        assertThat(generated.consumedBuildExecutions()).isEqualTo(1);
        assertThat(generated.patches())
                .allSatisfy(patch -> assertThat(patch.unifiedDiff()).contains("+++ b/"));

        CodingTask published = service.approve(generated.taskId(), "代码与测试已复核");

        assertThat(published.stage()).isEqualTo(CodingTaskStage.PUBLISHED);
        assertThat(Path.of(published.approvedOutputPath())).isDirectory();
        assertThat(Path.of(published.approvedOutputPath())
                .resolve("src/main/java/demo/generated/OrderExportPolicy.java"))
                .exists();
    }

    @Test
    void shouldBlockPublishingWhenVerificationFails() {
        Path root = testRoot();
        CodingTaskService service = service(root, false, WorkflowStage.COMPLETED);
        CodingTask failed = service.create("workflow-1");

        assertThat(failed.stage()).isEqualTo(CodingTaskStage.FAILED);
        assertThatThrownBy(() -> service.approve(failed.taskId(), "force"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("测试通过");
    }

    @Test
    void shouldRequireApprovedTechnicalWorkflow() {
        Path root = testRoot();
        CodingTaskService service = service(root, true, WorkflowStage.WAITING_APPROVAL);

        assertThatThrownBy(() -> service.create("workflow-1"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("先完成人工审批");
    }

    private CodingTaskService service(
            Path root,
            boolean buildPassed,
            WorkflowStage stage
    ) {
        RequirementWorkflowService workflowService = mock(RequirementWorkflowService.class);
        WorkflowState workflow = mock(WorkflowState.class);
        when(workflowService.get("workflow-1")).thenReturn(workflow);
        when(workflow.stage()).thenReturn(stage);

        CodePatchGenerator generator = ignored ->
                new RuleBasedCodePatchGenerator().generate(ignored);
        SandboxBuildRunner runner = (workspace, budget) -> new BuildVerification(
                buildPassed,
                "docker test",
                buildPassed ? 0 : 1,
                20,
                buildPassed ? "tests passed" : "tests failed"
        );
        Path sandbox = root.resolve("sandboxes");
        Path approved = root.resolve("approved");
        assertThat(Files.notExists(sandbox)).isTrue();
        return new CodingTaskService(
                workflowService,
                generator,
                new SandboxPolicy(),
                new SandboxProjectTemplate(),
                runner,
                new UnifiedDiffRenderer(),
                sandbox.toString(),
                approved.toString()
        );
    }

    private Path testRoot() {
        return Path.of(".codex-target", "test-workspaces", UUID.randomUUID().toString())
                .toAbsolutePath();
    }
}
