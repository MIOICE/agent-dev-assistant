package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.skill.CodingSkillPhase;
import com.gaozhaoyang.agent.skill.CodingSkillProvider;
import com.gaozhaoyang.agent.skill.SkillActivation;
import com.gaozhaoyang.agent.workflow.RequirementWorkflowService;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

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
            CodingTaskStage.CANCELLED,
            CodingTaskStage.TIMED_OUT,
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
    private final CodingTaskEventStream eventStream;
    private final TaskExecutor taskExecutor;
    private final Path sandboxRoot;
    private final Path approvedRoot;
    private final long maxTaskRuntimeMs;
    private final Set<String> runningTaskIds = ConcurrentHashMap.newKeySet();

    @Autowired
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
            CodingTaskEventStream eventStream,
            @Qualifier("codingTaskExecutor") TaskExecutor taskExecutor,
            @Value("${app.coding.sandbox-root}") String sandboxRoot,
            @Value("${app.coding.approved-root}") String approvedRoot,
            @Value("${app.coding.executor.max-task-runtime-ms:300000}") long maxTaskRuntimeMs
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
        this.eventStream = eventStream;
        this.taskExecutor = taskExecutor;
        this.sandboxRoot = configuredRoot(sandboxRoot, "沙箱");
        this.approvedRoot = configuredRoot(approvedRoot, "批准产物");
        if (maxTaskRuntimeMs <= 0) {
            throw new CodingTaskException("代码任务最长运行时间必须大于0毫秒");
        }
        this.maxTaskRuntimeMs = maxTaskRuntimeMs;
    }

    /** 兼容单元测试与阶段19之前的显式构造方式。 */
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
            CodingTaskEventStream eventStream,
            TaskExecutor taskExecutor,
            String sandboxRoot,
            String approvedRoot
    ) {
        this(workflowService, patchGenerator, patchRepairer, skillProvider,
                sandboxPolicy, projectTemplate, buildRunner, diffRenderer,
                taskRepository, eventStream, taskExecutor, sandboxRoot, approvedRoot,
                300_000);
    }

    public synchronized CodingTask submit(String workflowId) {
        Optional<CodingTask> existing = findByWorkflowId(workflowId);
        if (existing.isPresent()
                && existing.get().stage() != CodingTaskStage.FAILED
                && existing.get().stage() != CodingTaskStage.CANCELLED
                && existing.get().stage() != CodingTaskStage.TIMED_OUT) {
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
        save(queued);
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
            save(queued);
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

    public SseEmitter stream(String taskId) {
        get(taskId);
        return eventStream.subscribe(taskId, () -> get(taskId));
    }

    public CodingTaskRuntimeStatus runtimeStatus() {
        if (taskExecutor instanceof ThreadPoolTaskExecutor springExecutor) {
            ThreadPoolExecutor executor = springExecutor.getThreadPoolExecutor();
            return new CodingTaskRuntimeStatus(
                    springExecutor.getCorePoolSize(),
                    springExecutor.getActiveCount(),
                    springExecutor.getPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity(),
                    runningTaskIds.size(),
                    maxTaskRuntimeMs
            );
        }
        return new CodingTaskRuntimeStatus(
                -1, -1, -1, -1, -1, runningTaskIds.size(), maxTaskRuntimeMs);
    }

    public synchronized CodingTask cancel(String taskId, String reason) {
        CodingTask current = get(taskId);
        if (current.stage() == CodingTaskStage.CANCELLED) {
            return current;
        }
        if (current.stage() == CodingTaskStage.TIMED_OUT) {
            return current;
        }
        if (current.stage() == CodingTaskStage.PUBLISHED) {
            throw new CodingTaskException("已发布的代码任务不能取消");
        }
        if (current.stage() == CodingTaskStage.FAILED) {
            throw new CodingTaskException("已失败的代码任务无需取消，可重新提交");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        String message = normalizedReason.isBlank()
                ? "用户取消代码任务"
                : "用户取消代码任务：" + normalizedReason;
        return terminateTask(
                current,
                CodingTaskStage.CANCELLED,
                AgentLoopStopCode.CANCELLED,
                message,
                current.verification(),
                "CANCELLED"
        );
    }

    public synchronized CodingTask approve(String taskId, String comment) {
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
            Path buildDescriptor = workspace.resolve("pom.xml");
            byte[] verifiedBuildDescriptor = projectTemplate
                    .readVerifiedBuildDescriptor(buildDescriptor);
            Map<String, byte[]> verifiedPatchContents = new HashMap<>();
            for (PatchFile patch : current.patches()) {
                Path source = sandboxPolicy.resolveContained(workspace, patch.relativePath());
                if (!Files.isRegularFile(source)
                        || Files.isSymbolicLink(source)
                        || Files.size(source) != patch.bytes()) {
                    throw new CodingTaskException(
                            "沙箱文件在审批前发生变化，拒绝发布：" + patch.relativePath());
                }
                byte[] content = Files.readAllBytes(source);
                if (!sha256(content).equals(patch.sha256())) {
                    throw new CodingTaskException(
                            "沙箱文件在审批前发生变化，拒绝发布：" + patch.relativePath());
                }
                verifiedPatchContents.put(patch.relativePath(), content);
            }

            Path output = prepareTaskDirectory(approvedRoot, taskId);
            Files.write(output.resolve("pom.xml"), verifiedBuildDescriptor);
            for (PatchFile patch : current.patches()) {
                byte[] content = verifiedPatchContents.get(patch.relativePath());
                Path target = sandboxPolicy.resolveContained(output, patch.relativePath());
                Files.createDirectories(target.getParent());
                Files.write(target, content);
            }
            String normalizedComment = comment == null ? "" : comment.trim();
            CodingTask published = evolve(
                    current, CodingTaskStage.PUBLISHED, current.summary(),
                    current.consumedFiles(), current.consumedBytes(),
                    current.consumedBuildExecutions(), current.consumedDurationMs(),
                    current.repairAttempts(), current.patches(), current.verification(),
                    current.buildAttempts(), appendEvent(current.events(), "PUBLISHED",
                            normalizedComment.isBlank()
                                    ? "人工审批通过，产物已复制到批准目录"
                                    : "人工审批通过：" + normalizedComment),
                    current.workspaceId(), output.toString(), ""
            );
            return save(withAgentLoop(
                    published,
                    published.agentLoop().stop(
                            AgentLoopStopCode.GOAL_REACHED,
                            "人工审批完成，隔离代码产物已发布"
                    )
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
            fail(current, "后台执行队列已满，请稍后重新提交", current.verification(),
                    "QUEUE_REJECTED");
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
            current = ensureExecutionAllowed(current);
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
            current = save(evolve(
                    current, CodingTaskStage.GENERATING, current.summary(),
                    current.consumedFiles(), current.consumedBytes(),
                    current.consumedBuildExecutions(), current.consumedDurationMs(),
                    current.repairAttempts(), current.patches(), current.verification(),
                    current.buildAttempts(), appendEvent(current.events(), "EXECUTION_STARTED",
                            "后台工作线程开始生成代码补丁"),
                    workspaceId, "", ""
            ));

            SkillActivation generationSkills = skillProvider.activate(
                    CodingSkillPhase.GENERATION, generationSkillContext(workflow));
            current = recordSkillActivation(current, generationSkills, "代码生成");
            CodePatchPlan plan = sandboxPolicy.validate(
                    patchGenerator.generate(workflow, generationSkills), current.budget());
            current = ensureExecutionAllowed(current);
            Path workspace = prepareTaskDirectory(sandboxRoot, workspaceId);
            projectTemplate.initialize(workspace);
            List<PatchFile> patches = writeGeneratedFiles(workspace, plan.files(), Map.of());
            long bytes = patches.stream().mapToLong(PatchFile::bytes).sum();
            current = save(evolve(
                    current, CodingTaskStage.VERIFYING, plan.summary(),
                    patches.size(), bytes, current.consumedBuildExecutions(),
                    current.consumedDurationMs(), current.repairAttempts(), patches,
                    current.verification(), current.buildAttempts(),
                    appendEvent(current.events(), "PATCH_GENERATED",
                            "生成并校验 " + patches.size() + " 个文件，共 " + bytes + " 字节"),
                    workspaceId, "", ""
            ));

            current = recordAgentLoopStep(
                    current,
                    AgentLoopAction.GENERATE_PATCH,
                    "技术方案已经人工审批，沙箱工作区为空",
                    "先生成满足已审批方案的最小Java补丁，再交给策略层和沙箱验证",
                    "生成并通过路径、能力、文件数和字节数校验："
                            + patches.size() + "个文件",
                    actionFingerprint("GENERATE", planFingerprint(plan)),
                    true
            );

            executeControlledAgentLoop(current, workflow, workspace, plan);
        } catch (TaskControlSignal ignored) {
            // 取消或超时状态已经写入Checkpoint；工作线程只负责停止后续动作。
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

    private void executeControlledAgentLoop(
            CodingTask startingTask,
            WorkflowState workflow,
            Path workspace,
            CodePatchPlan startingPlan
    ) throws IOException {
        CodingTask current = startingTask;
        CodePatchPlan plan = startingPlan;

        while (true) {
            current = ensureExecutionAllowed(current);
            if (current.agentLoop().exhausted()) {
                stopTask(
                        current,
                        AgentLoopStopCode.STEP_BUDGET_EXHAUSTED,
                        "Agent Loop步骤预算已耗尽，任务安全停止",
                        current.verification(),
                        "LOOP_STOPPED"
                );
                return;
            }
            long remainingDuration = current.budget().maxDurationMs()
                    - current.consumedDurationMs();
            if (remainingDuration <= 0
                    || current.consumedBuildExecutions()
                    >= current.budget().maxBuildExecutions()) {
                stopTask(
                        current,
                        AgentLoopStopCode.EXECUTION_BUDGET_EXHAUSTED,
                        "代码任务已耗尽构建次数或执行时长预算",
                        current.verification(),
                        "BUDGET_EXHAUSTED"
                );
                return;
            }

            int buildNumber = current.consumedBuildExecutions() + 1;
            current = save(evolve(
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
            current = ensureExecutionAllowed(current);
            long consumedDuration = current.consumedDurationMs()
                    + Math.max(verification.durationMs(), 0);
            List<BuildAttempt> attempts = new ArrayList<>(current.buildAttempts());
            attempts.add(new BuildAttempt(buildNumber, verification, Instant.now()));

            String buildFingerprint = actionFingerprint(
                    "BUILD",
                    planFingerprint(plan),
                    String.valueOf(verification.passed()),
                    String.valueOf(verification.exitCode()),
                    verification.outputSummary()
            );
            boolean repeatedBuild = current.agentLoop().hasFingerprint(buildFingerprint);
            current = save(evolve(
                    current, CodingTaskStage.VERIFYING, plan.summary(),
                    current.consumedFiles(), current.consumedBytes(), buildNumber,
                    consumedDuration, current.repairAttempts(), current.patches(),
                    verification, attempts,
                    appendEvent(current.events(), "BUILD_COMPLETED",
                            "第 " + buildNumber + " 次沙箱测试"
                                    + (verification.passed() ? "通过" : "失败")),
                    current.workspaceId(), "", ""
            ));
            current = recordAgentLoopStep(
                    current,
                    AgentLoopAction.RUN_SANDBOX_TEST,
                    buildNumber == 1
                            ? "候选补丁尚未在隔离环境中验证"
                            : "上一轮补丁已根据失败证据修订，需要重新验证",
                    "真实编译和测试结果比模型自评更可靠",
                    verification.passed()
                            ? "Docker沙箱测试通过，exit code 0"
                            : "Docker沙箱测试失败，exit code " + verification.exitCode(),
                    buildFingerprint,
                    verification.passed() || !repeatedBuild
            );

            if (verification.passed()) {
                if (!current.agentLoop().exhausted()) {
                    current = recordAgentLoopStep(
                            current,
                            AgentLoopAction.REQUEST_HUMAN_APPROVAL,
                            "候选补丁已经通过隔离构建与自动化测试",
                            "代码执行属于高风险副作用，发布前必须由人检查Diff和测试证据",
                            "Agent暂停自主执行，等待人工审批",
                            actionFingerprint("APPROVAL", planFingerprint(plan)),
                            true
                    );
                }
                current = withAgentLoop(
                        current,
                        current.agentLoop().stop(
                                AgentLoopStopCode.WAITING_HUMAN_APPROVAL,
                                "沙箱测试通过，等待人工审批后发布"
                        )
                );
                save(evolve(
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

            if (repeatedBuild || current.agentLoop().consecutiveNoProgress() >= 2) {
                stopTask(
                        current,
                        AgentLoopStopCode.NO_PROGRESS,
                        "检测到相同补丁产生相同失败证据，停止无进展循环",
                        verification,
                        "NO_PROGRESS_DETECTED"
                );
                return;
            }

            if (buildNumber >= current.budget().maxBuildExecutions()
                    || consumedDuration >= current.budget().maxDurationMs()) {
                stopTask(
                        current,
                        AgentLoopStopCode.EXECUTION_BUDGET_EXHAUSTED,
                        "达到最大自动修复次数，任务已安全停止",
                        verification,
                        "BUDGET_EXHAUSTED"
                );
                return;
            }

            if (current.agentLoop().exhausted()) {
                stopTask(
                        current,
                        AgentLoopStopCode.STEP_BUDGET_EXHAUSTED,
                        "没有剩余Agent Loop步骤用于安全修复",
                        verification,
                        "LOOP_STOPPED"
                );
                return;
            }

            int repairNumber = current.repairAttempts() + 1;
            current = save(evolve(
                    current, CodingTaskStage.REPAIRING, plan.summary(),
                    current.consumedFiles(), current.consumedBytes(), buildNumber,
                    consumedDuration, repairNumber, current.patches(), verification, attempts,
                    appendEvent(current.events(), "REPAIR_STARTED",
                            "第 " + buildNumber + " 次测试失败，开始第 "
                                    + repairNumber + " 轮有界修复"),
                    current.workspaceId(), "", ""
            ));

            SkillActivation repairSkills = skillProvider.activate(
                    CodingSkillPhase.REPAIR,
                    generationSkillContext(workflow) + "\n" + verification.outputSummary());
            current = recordSkillActivation(current, repairSkills, "失败修复");
            CodePatchPlan repaired = sandboxPolicy.validate(
                    patchRepairer.repair(
                            workflow, plan, verification, repairNumber, repairSkills),
                    current.budget()
            );
            current = ensureExecutionAllowed(current);
            ensureSamePaths(plan, repaired);
            String previousPlanFingerprint = planFingerprint(plan);
            String repairedPlanFingerprint = planFingerprint(repaired);
            String repairFingerprint = actionFingerprint(
                    "REPAIR", previousPlanFingerprint, repairedPlanFingerprint,
                    verification.outputSummary()
            );
            boolean repairChangedFiles = !previousPlanFingerprint.equals(repairedPlanFingerprint);
            boolean repeatedRepair = current.agentLoop().hasFingerprint(repairFingerprint);
            current = recordAgentLoopStep(
                    current,
                    AgentLoopAction.REPAIR_PATCH,
                    "第 " + buildNumber + " 次沙箱测试失败，已获得受限构建日志",
                    "只允许基于失败证据修改原文件集合，并重新经过安全策略校验",
                    repairChangedFiles && !repeatedRepair
                            ? "生成了内容不同的修订补丁，准备再次验证"
                            : "修复结果与既有候选相同，未产生有效进展",
                    repairFingerprint,
                    repairChangedFiles && !repeatedRepair
            );
            if (!repairChangedFiles || repeatedRepair
                    || current.agentLoop().consecutiveNoProgress() >= 2) {
                stopTask(
                        current,
                        AgentLoopStopCode.NO_PROGRESS,
                        "修复Agent返回了重复补丁，继续测试不会改变结果",
                        verification,
                        "NO_PROGRESS_DETECTED"
                );
                return;
            }
            Map<String, String> previousFiles = new HashMap<>();
            for (GeneratedFile file : plan.files()) {
                previousFiles.put(file.relativePath(), file.content());
            }
            List<PatchFile> repairedPatches = writeGeneratedFiles(
                    workspace, repaired.files(), previousFiles);
            long repairedBytes = repairedPatches.stream().mapToLong(PatchFile::bytes).sum();
            current = save(evolve(
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
        return fail(current, message, verification, "FAILED");
    }

    private CodingTask fail(
            CodingTask current,
            String message,
            BuildVerification verification,
            String eventType
    ) {
        return terminateTask(
                current,
                CodingTaskStage.FAILED,
                AgentLoopStopCode.FAILURE,
                message,
                verification,
                eventType
        );
    }

    private CodingTask stopTask(
            CodingTask current,
            AgentLoopStopCode stopCode,
            String message,
            BuildVerification verification,
            String eventType
    ) {
        return terminateTask(
                current,
                CodingTaskStage.FAILED,
                stopCode,
                message,
                verification,
                eventType
        );
    }

    private CodingTask terminateTask(
            CodingTask current,
            CodingTaskStage terminalStage,
            AgentLoopStopCode stopCode,
            String message,
            BuildVerification verification,
            String eventType
    ) {
        CodingTask stopped = withAgentLoop(
                current,
                current.agentLoop().stop(stopCode, message)
        );
        return save(evolve(
                stopped,
                terminalStage, current.summary(),
                current.consumedFiles(), current.consumedBytes(),
                current.consumedBuildExecutions(), current.consumedDurationMs(),
                current.repairAttempts(), current.patches(), verification,
                current.buildAttempts(), appendEvent(current.events(), eventType, message),
                current.workspaceId(), "", message
        ));
    }

    private CodingTask ensureExecutionAllowed(CodingTask fallback) {
        CodingTask latest = taskRepository.findById(fallback.taskId()).orElse(fallback);
        if (latest.stage() == CodingTaskStage.CANCELLED
                || latest.stage() == CodingTaskStage.TIMED_OUT) {
            throw new TaskControlSignal();
        }
        Instant createdAt = latest.createdAt();
        if (createdAt != null
                && !Instant.now().isBefore(createdAt.plusMillis(maxTaskRuntimeMs))) {
            terminateTask(
                    latest,
                    CodingTaskStage.TIMED_OUT,
                    AgentLoopStopCode.DEADLINE_EXCEEDED,
                    "代码任务超过最长运行时间 " + maxTaskRuntimeMs + "ms，已安全停止",
                    latest.verification(),
                    "DEADLINE_EXCEEDED"
            );
            throw new TaskControlSignal();
        }
        return latest;
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
        return save(new CodingTask(
                current.taskId(), current.workflowId(), current.workflowSnapshot(), current.stage(),
                current.summary(), current.budget(), current.consumedFiles(),
                current.consumedBytes(), current.consumedBuildExecutions(),
                current.consumedDurationMs(), current.repairAttempts(), activatedSkills,
                current.patches(), current.verification(), current.buildAttempts(),
                appendEvent(current.events(), "SKILLS_ACTIVATED",
                        phase + "阶段按需加载：" + activation.routingSummary()),
                current.agentLoop(),
                current.workspaceId(), current.approvedOutputPath(), current.failureMessage(),
                current.createdAt(), Instant.now()
        ));
    }

    private String generationSkillContext(WorkflowState workflow) {
        return workflow.effectiveRequirement() + "\n"
                + String.valueOf(workflow.requirementCard()) + "\n"
                + String.valueOf(workflow.technicalSolution());
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
                buildAttempts, events, base.agentLoop(), workspaceId,
                approvedOutputPath, failureMessage,
                base.createdAt(), Instant.now()
        );
    }

    private CodingTask recordAgentLoopStep(
            CodingTask current,
            AgentLoopAction action,
            String observation,
            String rationale,
            String outcome,
            String fingerprint,
            boolean progressMade
    ) {
        AgentLoopState next = current.agentLoop().record(
                action, observation, rationale, outcome, fingerprint, progressMade);
        return save(withAgentLoop(current, next));
    }

    private synchronized CodingTask save(CodingTask task) {
        Optional<CodingTask> persisted = taskRepository.findById(task.taskId());
        if (persisted.isPresent()
                && (persisted.get().stage() == CodingTaskStage.CANCELLED
                    || persisted.get().stage() == CodingTaskStage.TIMED_OUT)
                && task.stage() != persisted.get().stage()) {
            return persisted.get();
        }
        CodingTask saved = taskRepository.save(task);
        eventStream.publish(saved);
        return saved;
    }

    private CodingTask withAgentLoop(CodingTask current, AgentLoopState agentLoop) {
        return new CodingTask(
                current.taskId(), current.workflowId(), current.workflowSnapshot(),
                current.stage(), current.summary(), current.budget(),
                current.consumedFiles(), current.consumedBytes(),
                current.consumedBuildExecutions(), current.consumedDurationMs(),
                current.repairAttempts(), current.activatedSkills(), current.patches(),
                current.verification(), current.buildAttempts(), current.events(), agentLoop,
                current.workspaceId(), current.approvedOutputPath(), current.failureMessage(),
                current.createdAt(), Instant.now()
        );
    }

    private String planFingerprint(CodePatchPlan plan) {
        StringBuilder canonical = new StringBuilder();
        plan.files().stream()
                .sorted(Comparator.comparing(GeneratedFile::relativePath))
                .forEach(file -> canonical
                        .append(file.relativePath()).append('\u0000')
                        .append(file.content()).append('\u0000'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String actionFingerprint(String... values) {
        return sha256(String.join("\u001f", values).getBytes(StandardCharsets.UTF_8));
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

    private static final class TaskControlSignal extends RuntimeException {
    }
}
