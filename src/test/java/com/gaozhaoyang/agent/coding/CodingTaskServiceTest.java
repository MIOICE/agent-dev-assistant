package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.RequirementWorkflowService;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.io.IOException;
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
        assertThat(generated.agentLoop().steps())
                .extracting(AgentLoopStep::action)
                .containsExactly(
                        AgentLoopAction.GENERATE_PATCH,
                        AgentLoopAction.RUN_SANDBOX_TEST,
                        AgentLoopAction.REQUEST_HUMAN_APPROVAL
                );
        assertThat(generated.agentLoop().stopCode())
                .isEqualTo(AgentLoopStopCode.WAITING_HUMAN_APPROVAL);
        assertThat(generated.patches())
                .allSatisfy(patch -> assertThat(patch.unifiedDiff()).contains("+++ b/"));

        CodingTask published = service.approve(generated.taskId(), "代码与测试已复核");

        assertThat(published.stage()).isEqualTo(CodingTaskStage.PUBLISHED);
        assertThat(published.agentLoop().stopCode())
                .isEqualTo(AgentLoopStopCode.GOAL_REACHED);
        assertThat(Path.of(published.approvedOutputPath())).isDirectory();
        assertThat(Path.of(published.approvedOutputPath())
                .resolve("src/main/java/demo/generated/OrderExportPolicy.java"))
                .exists();
    }

    @Test
    void shouldRejectTamperedBuildDescriptorWithoutLeavingPartialRelease() throws IOException {
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
        Path pom = root.resolve("sandboxes")
                .resolve(generated.workspaceId())
                .resolve("pom.xml");
        Files.writeString(pom, "<project>tampered</project>");

        assertThatThrownBy(() -> service.approve(generated.taskId(), "approve"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("pom.xml");
        assertThat(root.resolve("approved").resolve(generated.taskId()))
                .doesNotExist();
        assertThat(service.get(generated.taskId()).stage())
                .isEqualTo(CodingTaskStage.WAITING_APPROVAL);
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
            return changedPlan(plan, attempt);
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
        assertThat(task.agentLoop().stopCode())
                .isEqualTo(AgentLoopStopCode.WAITING_HUMAN_APPROVAL);
        assertThat(task.agentLoop().steps())
                .extracting(AgentLoopStep::action)
                .containsExactly(
                        AgentLoopAction.GENERATE_PATCH,
                        AgentLoopAction.RUN_SANDBOX_TEST,
                        AgentLoopAction.REPAIR_PATCH,
                        AgentLoopAction.RUN_SANDBOX_TEST,
                        AgentLoopAction.REQUEST_HUMAN_APPROVAL
                );
    }

    @Test
    void shouldStopAfterAutonomyBudgetIsExhausted() {
        Path root = testRoot();
        AtomicInteger repairCalls = new AtomicInteger();
        CodePatchRepairer repairer = (workflow, plan, failure, attempt, skills) -> {
            repairCalls.incrementAndGet();
            return changedPlan(plan, attempt);
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
        assertThat(failed.agentLoop().stopCode())
                .isEqualTo(AgentLoopStopCode.EXECUTION_BUDGET_EXHAUSTED);
        assertThatThrownBy(() -> service.approve(failed.taskId(), "force"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("测试通过");
    }

    @Test
    void shouldStopWhenRepairDoesNotChangePatch() {
        Path root = testRoot();
        AtomicInteger buildCalls = new AtomicInteger();
        CodingTaskService service = service(
                root,
                (workspace, budget) -> {
                    buildCalls.incrementAndGet();
                    return verification(false, "same compile failure");
                },
                (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED,
                new InMemoryCodingTaskRepository(),
                Runnable::run
        );

        CodingTask failed = service.submit("workflow-1");

        assertThat(failed.stage()).isEqualTo(CodingTaskStage.FAILED);
        assertThat(failed.agentLoop().stopCode())
                .isEqualTo(AgentLoopStopCode.NO_PROGRESS);
        assertThat(failed.failureMessage()).contains("重复补丁");
        assertThat(failed.consumedBuildExecutions()).isEqualTo(1);
        assertThat(buildCalls).hasValue(1);
        assertThat(failed.agentLoop().steps().getLast().progressMade()).isFalse();
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
    void shouldDeduplicateRepeatedSubmissionWhileTaskIsActive() {
        Path root = testRoot();
        CapturingTaskExecutor executor = new CapturingTaskExecutor();
        CodingTaskService service = service(
                root, passedRunner(), (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED, new InMemoryCodingTaskRepository(), executor);

        CodingTask first = service.submit("workflow-1");
        CodingTask repeated = service.submit("workflow-1");

        assertThat(repeated.taskId()).isEqualTo(first.taskId());
        assertThat(executor.submitted).isEqualTo(1);
    }

    @Test
    void shouldCancelQueuedTaskIdempotentlyAndRejectStaleWorkerUpdate() {
        Path root = testRoot();
        CapturingTaskExecutor executor = new CapturingTaskExecutor();
        CodingTaskService service = service(
                root, passedRunner(), (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED, new InMemoryCodingTaskRepository(), executor);
        CodingTask queued = service.submit("workflow-1");

        CodingTask cancelled = service.cancel(queued.taskId(), "业务方撤回需求");
        CodingTask repeated = service.cancel(queued.taskId(), "重复点击");
        executor.pending.run();

        CodingTask persisted = service.get(queued.taskId());
        assertThat(cancelled.stage()).isEqualTo(CodingTaskStage.CANCELLED);
        assertThat(cancelled.agentLoop().stopCode()).isEqualTo(AgentLoopStopCode.CANCELLED);
        assertThat(cancelled.failureMessage()).contains("业务方撤回需求");
        assertThat(repeated).isEqualTo(cancelled);
        assertThat(persisted).isEqualTo(cancelled);
    }

    @Test
    void shouldStopRecoveredTaskWhenWallClockDeadlineIsExceeded() {
        Path root = testRoot();
        InMemoryCodingTaskRepository repository = new InMemoryCodingTaskRepository();
        Instant old = Instant.now().minusSeconds(2);
        repository.save(new CodingTask(
                "task-timeout", "workflow-1", completedWorkflow(),
                CodingTaskStage.QUEUED, "", AutonomyBudget.safeDefault(),
                0, 0, 0, 0, 0, List.of(), List.of(), null, List.of(),
                List.of(CodingTaskEvent.of("QUEUED", "等待执行")),
                "", "", "", old, old
        ));
        CodingTaskService service = service(
                root, passedRunner(), (workflow, plan, failure, attempt, skills) -> plan,
                WorkflowStage.COMPLETED, repository, Runnable::run, 10);

        service.recoverInterruptedTasks();

        CodingTask timedOut = service.get("task-timeout");
        assertThat(timedOut.stage()).isEqualTo(CodingTaskStage.TIMED_OUT);
        assertThat(timedOut.agentLoop().stopCode())
                .isEqualTo(AgentLoopStopCode.DEADLINE_EXCEEDED);
        assertThat(timedOut.events()).extracting(CodingTaskEvent::type)
                .contains("RECOVERED", "DEADLINE_EXCEEDED");
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
        return service(root, runner, repairer, stage, repository, executor, 300_000);
    }

    private CodingTaskService service(
            Path root,
            SandboxBuildRunner runner,
            CodePatchRepairer repairer,
            WorkflowStage stage,
            CodingTaskRepository repository,
            TaskExecutor executor,
            long maxTaskRuntimeMs
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
                new CodingTaskEventStream(),
                executor,
                sandbox.toString(),
                approved.toString(),
                maxTaskRuntimeMs
        );
    }

    private SandboxBuildRunner passedRunner() {
        return (workspace, budget) -> verification(true, "tests passed");
    }

    private static BuildVerification verification(boolean passed, String output) {
        return new BuildVerification(
                passed, "docker test", passed ? 0 : 1, 20, output);
    }

    private static CodePatchPlan changedPlan(CodePatchPlan plan, int attempt) {
        List<GeneratedFile> changed = plan.files().stream()
                .map(file -> new GeneratedFile(
                        file.relativePath(),
                        file.purpose(),
                        file.content() + "\n// bounded repair " + attempt
                ))
                .toList();
        return new CodePatchPlan("自动修复后的代码方案", changed);
    }

    private static WorkflowState completedWorkflow() {
        Instant now = Instant.now();
        return new WorkflowState(
                "workflow-1", "订单列表增加导出功能", List.of(),
                WorkflowStage.COMPLETED, null, List.of(), null, null, null, null, List.of(),
                List.of(), 1, now, now, List.of(), null
        );
    }

    private Path testRoot() {
        return Path.of(".codex-target", "test-workspaces", UUID.randomUUID().toString())
                .toAbsolutePath();
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {
        private Runnable pending;
        private int submitted;

        @Override
        public void execute(Runnable task) {
            submitted++;
            this.pending = task;
        }
    }
}
