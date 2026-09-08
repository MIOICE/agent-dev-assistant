package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.skill.CodingSkillPhase;
import com.gaozhaoyang.agent.skill.CodingSkillProvider;
import com.gaozhaoyang.agent.skill.SkillActivation;
import com.gaozhaoyang.agent.workflow.RequirementWorkflowService;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodingTaskService {

    private static final long MAX_SINGLE_BUILD_DURATION_MS = 60_000;
    private static final Set<CodingTaskStage> RECOVERABLE_STAGES = Set.of(
            CodingTaskStage.QUEUED,
            CodingTaskStage.GENERATING,
            CodingTaskStage.VERIFYING,
            CodingTaskStage.REPAIRING
    );
    private static final Set<CodingTaskStage> TERMINAL_OR_PAUSED_STAGES = Set.of(
            CodingTaskStage.WAITING_APPROVAL,
            CodingTaskStage.PUBLISHED,
            CodingTaskStage.FAILED
    );

    private final RequirementWorkflowService workflowService;
    private final CodePatchGenerator patchGenerator;
    private final CodePatchRepairer patchRepairer;
    private final CodingSkillProvider skillProvider;
    private final SandboxPolicy sandboxPolicy;
    private final SandboxProjectTemplate projectTemplate;
    private final SandboxBuildRunner buildRunner;
    private final UnifiedDiffRenderer diffRenderer;
    private final CodingTaskRepository taskRepository;
    private final TaskExecutor taskExecutor;
    private final Path sandboxRoot;
    private final Path approvedRoot;
    private final Set<String> runningTaskIds = ConcurrentHashMap.newKeySet();

    public CodingTaskService(
            RequirementWorkflowService workflowService,
            CodePatchGenerator patchGenerator,
            CodePatchRepairer patchRepairer,
            CodingSkillProvider skillProvider,
            SandboxPolicy sandboxPolicy,
            SandboxProjectTemplate projectTemplate,
            SandboxBuildRunner buildRunner,
            UnifiedDiffRenderer diffRenderer,
            CodingTaskRepository taskRepository,
            @Qualifier("codingTaskExecutor") TaskExecutor taskExecutor,
            @Value("${app.coding.sandbox-root}") String sandboxRoot,
            @Value("${app.coding.approved-root}") String approvedRoot
    ) {
        this.workflowService = workflowService;
        this.patchGenerator = patchGenerator;
        this.patchRepairer = patchRepairer;
        this.skillProvider = skillProvider;
        this.sandboxPolicy = sandboxPolicy;
        this.projectTemplate = projectTemplate;
        this.buildRunner = buildRunner;
        this.diffRenderer = diffRenderer;
        this.taskRepository = taskRepository;
        this.taskExecutor = taskExecutor;
        this.sandboxRoot = configuredRoot(sandboxRoot, "沙箱");
        this.approvedRoot = configuredRoot(approvedRoot, "批准产物");
    }

    public synchronized CodingTask submit(String workflowId) {
        Optional<CodingTask> existing = findByWorkflowId(workflowId);
        if (existing.isPresent() && existing.get().stage() != CodingTaskStage.FAILED) {
            return existing.get();
        }
        WorkflowState workflow = workflowService.get(workflowId);
        if (workflow.stage() != WorkflowStage.COMPLETED) {
            throw new CodingTaskException("技术方案必须先完成人工审批，才能生成代码补丁");
        }

        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        CodingTask queued = new CodingTask(
                taskId, workflowId, workflow, CodingTaskStage.QUEUED, "",
                AutonomyBudget.safeDefault(), 0, 0, 0, 0, 0,
                List.of(),
                List.of(), null, List.of(),
                List.of(CodingTaskEvent.of("QUEUED", "代码任务已进入后台执行队列")),
                "", "", "", now, now
        );
        taskRepository.save(queued);
        dispatch(taskId);
        return get(taskId);
    }

    /** 保留旧调用入口；现在的语义已经变为提交后台任务。 */
    public CodingTask create(String workflowId) {
        return submit(workflowId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        for (CodingTask interrupted : taskRepository.findByStages(RECOVERABLE_STAGES)) {
            CodingTask queued = evolve(
                    interrupted, CodingTaskStage.QUEUED, interrupted.summary(),
                    interrupted.consumedFiles(), interrupted.consumedBytes(),
                    interrupted.consumedBuildExecutions(), interrupted.consumedDurationMs(),
                    interrupted.repairAttempts(), interrupted.patches(),
                    interrupted.verification(), interrupted.buildAttempts(),
                    appendEvent(interrupted.events(), "RECOVERED",
                            "检测到未完成Checkpoint，任务重新进入队列"),
                    "", "", ""
            );
            taskRepository.save(queued);
            dispatch(queued.taskId());
        }
    }

    public CodingTask get(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new CodingTaskException("代码任务不存在：" + taskId));
    }

    public Optional<CodingTask> findByWorkflowId(String workflowId) {
        return taskRepository.findLatestByWorkflowId(workflowId);
    }

    public CodingTask approve(String taskId, String comment) {
        CodingTask current = get(taskId);
        if (current.stage() != CodingTaskStage.WAITING_APPROVAL
                || current.verification() == null
                || !current.verification().passed()) {
            throw new CodingTaskException("只有测试通过并等待审批的代码任务可以发布");
        }
        if (current.workspaceId().isBlank()) {
            throw new CodingTaskException("代码任务的沙箱工作区不存在");
        }
        Path workspace = sandboxPolicy.resolveContained(sandboxRoot, current.workspaceId());
        if (!Files.isDirectory(workspace)) {
            throw new CodingTaskException("代码任务的沙箱工作区不存在");
        }

        try {
            Path output = prepareTaskDirectory(approvedRoot, taskId);
            Files.copy(workspace.resolve("pom.xml"), output.resolve("pom.xml"));
            for (PatchFile patch : current.patches()) {
                Path source = sandboxPolicy.resolveContained(workspace, patch.relativePath());
                byte[] content = Files.readAllBytes(source);
                if (!sha256(content).equals(patch.sha256())) {
                    throw new CodingTaskException(
                            "沙箱文件在审批前发生变化，拒绝发布：" + patch.relativePath());
                }
                Path target = sandboxPolicy.resolveContained(output, patch.relativePath());
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
            String normalizedComment = comment == null ? "" : comment.trim();
            return taskRepository.save(evolve(
                    current, CodingTaskStage.PUBLISHED, current.summary(),
                    current.consumedFiles(), current.consumedBytes(),
                    current.consumedBuildExecutions(), current.consumedDurationMs(),
                    current.repairAttempts(), current.patches(), current.verification(),
                    current.buildAttempts(), appendEvent(current.events(), "PUBLISHED",
                            normalizedComment.isBlank()
                                    ? "人工审批通过，产物已复制到批准目录"
                                    : "人工审批通过：" + normalizedComment),
                    current.workspaceId(), output.toString(), ""
            ));
        } catch (IOException exception) {
            throw new CodingTaskException("批准产物写入失败", exception);
        }
    }

    private void dispatch(String taskId) {
        try {
            taskExecutor.execute(() -> process(taskId));
        } catch (RuntimeException exception) {
            CodingTask current = get(taskId);
            fail(current, "后台执行队列已满，请稍后重新提交", current.verification());
        }
    }

    private void process(String taskId) {
        if (!runningTaskIds.add(taskId)) {
            return;
        }
        CodingTask current = get(taskId);
        try {
            if (TERMINAL_OR_PAUSED_STAGES.contains(current.stage())) {
                return;
            }
            WorkflowState workflow = current.workflowSnapshot() != null
                    ? current.workflowSnapshot()
                    : workflowService.get(current.workflowId());
            if (workflow.stage() != WorkflowStage.COMPLETED) {
                fail(current, "关联技术方案不再是已审批状态，停止代码任务", null);
                return;
            }
            if (current.consumedBuildExecutions() >= current.budget().maxBuildExecutions()
                    || current.consumedDurationMs() >= current.budget().maxDurationMs()) {
                fail(current, "恢复任务时发现自治预算已经耗尽", current.verification());
                return;
            }

            String workspaceId = taskId + "-run-" + UUID.randomUUID();
            current = taskRepository.save(evolve(
                    current, CodingTaskStage.GENERATING, current.summary(),
                    current.consumedFiles(), current.consumedBytes(),
                    current.consumedBuildExecutions(), current.consumedDurationMs(),
                    current.repairAttempts(), current.patches(), current.verification(),
                    current.buildAttempts(), appendEvent(current.events(), "EXECUTION_STARTED",
                            "后台工作线程开始生成代码补丁"),
                    workspaceId, "", ""
            ));

            SkillActivation generationSkills = skillProvider.activate(CodingSkillPhase.GENERATION);
            current = recordSkillActivation(current, generationSkills, "代码生成");
            CodePatchPlan plan = sandboxPolicy.validate(
                    patchGenerator.generate(workflow, generationSkills), current.budget());
            Path workspace = prepareTaskDirectory(sandboxRoot, workspaceId);
            projectTemplate.initialize(workspace);
            List<PatchFile> patches = writeGeneratedFiles(workspace, plan.files(), Map.of());
            long bytes = patches.stream().mapToLong(PatchFile::bytes).sum();
            current = taskRepository.save(evolve(
                    current, CodingTaskStage.VERIFYING, plan.summary(),
                    patches.size(), bytes, current.consumedBuildExecutions(),
                    current.consumedDurationMs(), current.repairAttempts(), patches,
                    current.verification(), current.buildAttempts(),
                    appendEvent(current.events(), "PATCH_GENERATED",
                            "生成并校验 " + patches.size() + " 个文件，共 " + bytes + " 字节"),
                    workspaceId, "", ""
            ));

            executeRepairLoop(current, workflow, workspace, plan);
        } catch (IOException exception) {
            CodingTask latest = taskRepository.findById(taskId).orElse(current);
            fail(latest, "沙箱文件操作失败", latest.verification());
        } catch (CodingTaskException exception) {
            CodingTask latest = taskRepository.findById(taskId).orElse(current);
            fail(latest, exception.getMessage(), latest.verification());
        } catch (RuntimeException exception) {
            CodingTask latest = taskRepository.findById(taskId).orElse(current);
            fail(latest, "代码任务执行失败", latest.verification());
        } finally {
            runningTaskIds.remove(taskId);
        }
    }

    private void executeRepairLoop(
            CodingTask startingTask,
            WorkflowState workflow,
            Path workspace,
            CodePatchPlan startingPlan
    ) throws IOException {
        CodingTask current = startingTask;
        CodePatchPlan plan = startingPlan;

        while (true) {
            long remainingDuration = current.budget().maxDurationMs()
                    - current.consumedDurationMs();
            if (remainingDuration <= 0
                    || current.consumedBuildExecutions()
                    >= current.budget().maxBuildExecutions()) {
                fail(current, "代码任务已耗尽自治预算", current.verification());
                return;
            }

            int buildNumber = current.consumedBuildExecutions() + 1;
            current = taskRepository.save(evolve(
                    current, CodingTaskStage.VERIFYING, plan.summary(),
                    current.consumedFiles(), current.consumedBytes(), buildNumber,
                    current.consumedDurationMs(), current.repairAttempts(), current.patches(),
                    current.verification(), current.buildAttempts(),
                    appendEvent(current.events(), "BUILD_STARTED",
                            "开始第 " + buildNumber + " 次Docker沙箱测试"),
                    current.workspaceId(), "", ""
            ));

            AutonomyBudget remainingBudget = new AutonomyBudget(
                    current.budget().maxFiles(), current.budget().maxTotalBytes(),
                    current.budget().maxBuildExecutions() - buildNumber + 1,
                    Math.min(remainingDuration, MAX_SINGLE_BUILD_DURATION_MS)
            );
            BuildVerification verification = buildRunner.verify(workspace, remainingBudget);
            long consumedDuration = current.consumedDurationMs()
                    + Math.max(verification.durationMs(), 0);
            List<BuildAttempt> attempts = new ArrayList<>(current.buildAttempts());
            attempts.add(new BuildAttempt(buildNumber, verification, Instant.now()));

            if (verification.passed()) {
                taskRepository.save(evolve(
                        current, CodingTaskStage.WAITING_APPROVAL, plan.summary(),
                        current.consumedFiles(), current.consumedBytes(), buildNumber,
                        consumedDuration, current.repairAttempts(), current.patches(),
                        verification, attempts,
                        appendEvent(current.events(), "BUILD_PASSED",
                                "第 " + buildNumber + " 次沙箱测试通过，等待人工审批"),
                        current.workspaceId(), "", ""
                ));
                return;
            }

            if (buildNumber >= current.budget().maxBuildExecutions()
                    || consumedDuration >= current.budget().maxDurationMs()) {
                taskRepository.save(evolve(
                        current, CodingTaskStage.FAILED, plan.summary(),
                        current.consumedFiles(), current.consumedBytes(), buildNumber,
                        consumedDuration, current.repairAttempts(), current.patches(),
                        verification, attempts,
                        appendEvent(current.events(), "BUDGET_EXHAUSTED",
                                "沙箱测试仍未通过，自治预算已耗尽"),
                        current.workspaceId(), "",
                        "达到最大自动修复次数，任务已安全停止"
                ));
                return;
            }

            int repairNumber = current.repairAttempts() + 1;
            current = taskRepository.save(evolve(
                    current, CodingTaskStage.REPAIRING, plan.summary(),
                    current.consumedFiles(), current.consumedBytes(), buildNumber,
                    consumedDuration, repairNumber, current.patches(), verification, attempts,
                    appendEvent(current.events(), "REPAIR_STARTED",
                            "第 " + buildNumber + " 次测试失败，开始第 "
                                    + repairNumber + " 轮有界修复"),
                    current.workspaceId(), "", ""
            ));

            SkillActivation repairSkills = skillProvider.activate(CodingSkillPhase.REPAIR);
            current = recordSkillActivation(current, repairSkills, "失败修复");
            CodePatchPlan repaired = sandboxPolicy.validate(
                    patchRepairer.repair(
                            workflow, plan, verification, repairNumber, repairSkills),
                    current.budget()
            );
            ensureSamePaths(plan, repaired);
            Map<String, String> previousFiles = new HashMap<>();
            for (GeneratedFile file : plan.files()) {
                previousFiles.put(file.relativePath(), file.content());
            }
            List<PatchFile> repairedPatches = writeGeneratedFiles(
                    workspace, repaired.files(), previousFiles);
            long repairedBytes = repairedPatches.stream().mapToLong(PatchFile::bytes).sum();
            current = taskRepository.save(evolve(
                    current, CodingTaskStage.VERIFYING, repaired.summary(),
                    repairedPatches.size(), repairedBytes, buildNumber,
                    consumedDuration, repairNumber, repairedPatches, verification, attempts,
                    appendEvent(current.events(), "PATCH_REPAIRED",
                            "第 " + repairNumber + " 轮修复完成，准备重新测试"),
                    current.workspaceId(), "", ""
            ));
            plan = repaired;
        }
    }

    private List<PatchFile> writeGeneratedFiles(
            Path workspace,
            List<GeneratedFile> generatedFiles,
            Map<String, String> previousFiles
    ) throws IOException {
        List<PatchFile> patches = new ArrayList<>();
        boolean replacement = !previousFiles.isEmpty();
        for (GeneratedFile generated : generatedFiles) {
            Path target = sandboxPolicy.resolveContained(workspace, generated.relativePath());
            Files.createDirectories(target.getParent());
            if (!replacement && Files.exists(target)) {
                throw new CodingTaskException("初始生成阶段只允许新增文件：" + generated.relativePath());
            }
            if (replacement && Files.notExists(target)) {
                throw new CodingTaskException("修复阶段不能新增文件：" + generated.relativePath());
            }
            byte[] bytes = generated.content().getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
            String operation = replacement ? "MODIFY" : "ADD";
            String diff = replacement
                    ? diffRenderer.renderReplacement(
                            generated.relativePath(),
                            previousFiles.get(generated.relativePath()),
                            generated.content())
                    : diffRenderer.renderAddedFile(generated.relativePath(), generated.content());
            patches.add(new PatchFile(
                    generated.relativePath(), generated.purpose(), operation,
                    sha256(bytes), bytes.length, diff
            ));
        }
        return List.copyOf(patches);
    }

    private void ensureSamePaths(CodePatchPlan previous, CodePatchPlan repaired) {
        Set<String> oldPaths = new HashSet<>();
        for (GeneratedFile file : previous.files()) {
            oldPaths.add(file.relativePath());
        }
        Set<String> newPaths = new HashSet<>();
        for (GeneratedFile file : repaired.files()) {
            newPaths.add(file.relativePath());
        }
        if (!oldPaths.equals(newPaths)) {
            throw new CodingTaskException("自动修复不能新增、删除或重命名文件");
        }
    }

    private CodingTask fail(CodingTask current, String message, BuildVerification verification) {
        return taskRepository.save(evolve(
                current, CodingTaskStage.FAILED, current.summary(),
                current.consumedFiles(), current.consumedBytes(),
                current.consumedBuildExecutions(), current.consumedDurationMs(),
                current.repairAttempts(), current.patches(), verification,
                current.buildAttempts(), appendEvent(current.events(), "FAILED", message),
                current.workspaceId(), "", message
        ));
    }

    private CodingTask recordSkillActivation(
            CodingTask current,
            SkillActivation activation,
            String phase
    ) {
        List<String> activatedSkills = new ArrayList<>(current.activatedSkills());
        for (String name : activation.skillNames()) {
            if (!activatedSkills.contains(name)) {
                activatedSkills.add(name);
            }
        }
        return taskRepository.save(new CodingTask(
                current.taskId(), current.workflowId(), current.workflowSnapshot(), current.stage(),
                current.summary(), current.budget(), current.consumedFiles(),
                current.consumedBytes(), current.consumedBuildExecutions(),
                current.consumedDurationMs(), current.repairAttempts(), activatedSkills,
                current.patches(), current.verification(), current.buildAttempts(),
                appendEvent(current.events(), "SKILLS_ACTIVATED",
                        phase + "阶段按需加载：" + String.join("、", activation.skillNames())),
                current.workspaceId(), current.approvedOutputPath(), current.failureMessage(),
                current.createdAt(), Instant.now()
        ));
    }

    private CodingTask evolve(
            CodingTask base,
            CodingTaskStage stage,
            String summary,
            int consumedFiles,
            long consumedBytes,
            int consumedBuildExecutions,
            long consumedDurationMs,
            int repairAttempts,
            List<PatchFile> patches,
            BuildVerification verification,
            List<BuildAttempt> buildAttempts,
            List<CodingTaskEvent> events,
            String workspaceId,
            String approvedOutputPath,
            String failureMessage
    ) {
        return new CodingTask(
                base.taskId(), base.workflowId(), base.workflowSnapshot(), stage,
                summary, base.budget(),
                consumedFiles, consumedBytes, consumedBuildExecutions,
                consumedDurationMs, repairAttempts, base.activatedSkills(), patches, verification,
                buildAttempts, events, workspaceId, approvedOutputPath, failureMessage,
                base.createdAt(), Instant.now()
        );
    }

    private List<CodingTaskEvent> appendEvent(
            List<CodingTaskEvent> events,
            String type,
            String message
    ) {
        List<CodingTaskEvent> updated = new ArrayList<>(events);
        updated.add(CodingTaskEvent.of(type, message));
        return List.copyOf(updated);
    }

    private Path prepareTaskDirectory(Path root, String taskId) throws IOException {
        Files.createDirectories(root);
        Path taskDirectory = sandboxPolicy.resolveContained(root, taskId);
        if (Files.exists(taskDirectory)) {
            throw new CodingTaskException("任务目录已经存在，拒绝覆盖");
        }
        Files.createDirectory(taskDirectory);
        return taskDirectory;
    }

    private Path configuredRoot(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "目录不能为空");
        }
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (root.getNameCount() < 2) {
            throw new IllegalArgumentException(label + "目录范围过大");
        }
        return root;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前Java运行时不支持SHA-256", exception);
        }
    }
}
