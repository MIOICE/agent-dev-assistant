package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.RequirementWorkflowService;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingTaskServiceTest {

    @Test
    void shouldGenerateVerifyAndPublishOnlyAfterApproval() {
        Path root = testRoot();
        CodingTaskService service = service(
                root,
                passedRunner(),
                (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED,
                new InMemoryCodingTaskRepository(),
                Runnable::run
        );

        CodingTask generated = service.submit("workflow-1");

        assertThat(generated.stage()).isEqualTo(CodingTaskStage.WAITING_APPROVAL);
        assertThat(generated.patches()).hasSize(2);
        assertThat(generated.consumedBuildExecutions()).isEqualTo(1);
        assertThat(generated.activatedSkills())
                .containsExactly("java-code-generation", "security-review");
        assertThat(generated.buildAttempts()).hasSize(1);
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
    void shouldRepairFailedPatchAndPassOnSecondBuild() {
        Path root = testRoot();
        AtomicInteger buildCalls = new AtomicInteger();
        AtomicInteger repairCalls = new AtomicInteger();
        SandboxBuildRunner runner = (workspace, budget) -> {
            int call = buildCalls.incrementAndGet();
            return verification(call > 1, call > 1 ? "tests passed" : "compile failed");
        };
        CodePatchRepairer repairer = (workflow, plan, failure, attempt, skills) -> {
            repairCalls.incrementAndGet();
            return new CodePatchPlan("自动修复后的代码方案", plan.files());
        };
        CodingTaskService service = service(
                root, runner, repairer, WorkflowStage.COMPLETED,
                new InMemoryCodingTaskRepository(), Runnable::run);

        CodingTask task = service.submit("workflow-1");

        assertThat(task.stage()).isEqualTo(CodingTaskStage.WAITING_APPROVAL);
        assertThat(task.consumedBuildExecutions()).isEqualTo(2);
        assertThat(task.repairAttempts()).isEqualTo(1);
        assertThat(task.activatedSkills()).containsExactly(
                "java-code-generation", "security-review", "test-failure-repair");
        assertThat(task.buildAttempts()).hasSize(2);
        assertThat(task.buildAttempts().getFirst().verification().passed()).isFalse();
        assertThat(task.buildAttempts().getLast().verification().passed()).isTrue();
        assertThat(task.patches()).allSatisfy(
                patch -> assertThat(patch.operation()).isEqualTo("MODIFY"));
        assertThat(repairCalls).hasValue(1);
    }

    @Test
    void shouldStopAfterAutonomyBudgetIsExhausted() {
        Path root = testRoot();
        AtomicInteger repairCalls = new AtomicInteger();
        CodePatchRepairer repairer = (workflow, plan, failure, attempt, skills) -> {
            repairCalls.incrementAndGet();
            return plan;
        };
        CodingTaskService service = service(
                root,
                (workspace, budget) -> verification(false, "tests failed"),
                repairer,
                WorkflowStage.COMPLETED,
                new InMemoryCodingTaskRepository(),
                Runnable::run
        );

        CodingTask failed = service.submit("workflow-1");

        assertThat(failed.stage()).isEqualTo(CodingTaskStage.FAILED);
        assertThat(failed.consumedBuildExecutions()).isEqualTo(3);
        assertThat(failed.repairAttempts()).isEqualTo(2);
        assertThat(failed.buildAttempts()).hasSize(3);
        assertThat(failed.failureMessage()).contains("最大自动修复次数");
        assertThat(repairCalls).hasValue(2);
        assertThatThrownBy(() -> service.approve(failed.taskId(), "force"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("测试通过");
    }

    @Test
    void shouldReturnQueuedTaskBeforeBackgroundExecutionStarts() {
        Path root = testRoot();
        CapturingTaskExecutor executor = new CapturingTaskExecutor();
        CodingTaskService service = service(
                root, passedRunner(), (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED, new InMemoryCodingTaskRepository(), executor);

        CodingTask queued = service.submit("workflow-1");

        assertThat(queued.stage()).isEqualTo(CodingTaskStage.QUEUED);
        assertThat(executor.pending).isNotNull();

        executor.pending.run();
        assertThat(service.get(queued.taskId()).stage())
                .isEqualTo(CodingTaskStage.WAITING_APPROVAL);
    }

    @Test
    void shouldResumeInterruptedTaskFromCheckpoint() {
        Path root = testRoot();
        InMemoryCodingTaskRepository repository = new InMemoryCodingTaskRepository();
        Instant now = Instant.now();
        CodingTask interrupted = new CodingTask(
                "task-recovery", "workflow-1", completedWorkflow(),
                CodingTaskStage.REPAIRING,
                "等待恢复", AutonomyBudget.safeDefault(),
                2, 1000, 1, 20, 1,
                List.of("java-code-generation"),
                List.of(), verification(false, "previous failure"),
                List.of(new BuildAttempt(1, verification(false, "previous failure"), now)),
                List.of(CodingTaskEvent.of("REPAIR_STARTED", "服务关闭前正在修复")),
                "old-workspace", "", "", now, now
        );
        repository.save(interrupted);
        CodingTaskService service = service(
                root, passedRunner(), (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED, repository, Runnable::run);

        service.recoverInterruptedTasks();
        CodingTask recovered = service.get("task-recovery");

        assertThat(recovered.stage()).isEqualTo(CodingTaskStage.WAITING_APPROVAL);
        assertThat(recovered.consumedBuildExecutions()).isEqualTo(2);
        assertThat(recovered.buildAttempts()).hasSize(2);
        assertThat(recovered.events())
                .anySatisfy(event -> assertThat(event.type()).isEqualTo("RECOVERED"));
        assertThat(recovered.workspaceId()).isNotEqualTo("old-workspace");
    }

    @Test
    void shouldRequireApprovedTechnicalWorkflow() {
        Path root = testRoot();
        CodingTaskService service = service(
                root, passedRunner(), (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.WAITING_APPROVAL,
                new InMemoryCodingTaskRepository(), Runnable::run);

        assertThatThrownBy(() -> service.submit("workflow-1"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("先完成人工审批");
    }

    private CodingTaskService service(
            Path root,
            SandboxBuildRunner runner,
            CodePatchRepairer repairer,
            WorkflowStage stage,
            CodingTaskRepository repository,
            TaskExecutor executor
    ) {
        RequirementWorkflowService workflowService = mock(RequirementWorkflowService.class);
        WorkflowState workflow = mock(WorkflowState.class);
        when(workflowService.get("workflow-1")).thenReturn(workflow);
        when(workflow.stage()).thenReturn(stage);

        CodePatchGenerator generator = (ignored, skills) ->
                new RuleBasedCodePatchGenerator().generate(ignored, skills);
        Path sandbox = root.resolve("sandboxes");
        Path approved = root.resolve("approved");
        assertThat(Files.notExists(sandbox)).isTrue();
        return new CodingTaskService(
                workflowService,
                generator,
                repairer,
                (phase, taskContext) -> phase == com.gaozhaoyang.agent.skill.CodingSkillPhase.GENERATION
                        ? new com.gaozhaoyang.agent.skill.SkillActivation(
                                List.of("java-code-generation", "security-review"), "generation")
                        : new com.gaozhaoyang.agent.skill.SkillActivation(
                                List.of("test-failure-repair", "security-review"), "repair"),
                new SandboxPolicy(),
                new SandboxProjectTemplate(),
                runner,
                new UnifiedDiffRenderer(),
                repository,
                executor,
                sandbox.toString(),
                approved.toString()
        );
    }

    private SandboxBuildRunner passedRunner() {
        return (workspace, budget) -> verification(true, "tests passed");
    }

    private static BuildVerification verification(boolean passed, String output) {
        return new BuildVerification(
                passed, "docker test", passed ? 0 : 1, 20, output);
    }

    private static WorkflowState completedWorkflow() {
        Instant now = Instant.now();
        return new WorkflowState(
                "workflow-1", "订单列表增加导出功能", List.of(),
                WorkflowStage.COMPLETED, null, List.of(), null, null, null, List.of(),
                List.of(), 1, now, now, List.of(), null
        );
    }

    private Path testRoot() {
        return Path.of(".codex-target", "test-workspaces", UUID.randomUUID().toString())
                .toAbsolutePath();
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {
        private Runnable pending;

        @Override
        public void execute(Runnable task) {
            this.pending = task;
        }
    }
}
