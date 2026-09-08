package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.RequirementWorkflowService;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodingTaskService {

    private final RequirementWorkflowService workflowService;
    private final CodePatchGenerator patchGenerator;
    private final SandboxPolicy sandboxPolicy;
    private final SandboxProjectTemplate projectTemplate;
    private final SandboxBuildRunner buildRunner;
    private final UnifiedDiffRenderer diffRenderer;
    private final Path sandboxRoot;
    private final Path approvedRoot;
    private final Map<String, CodingTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, Path> taskWorkspaces = new ConcurrentHashMap<>();

    public CodingTaskService(
            RequirementWorkflowService workflowService,
            CodePatchGenerator patchGenerator,
            SandboxPolicy sandboxPolicy,
            SandboxProjectTemplate projectTemplate,
            SandboxBuildRunner buildRunner,
            UnifiedDiffRenderer diffRenderer,
            @Value("${app.coding.sandbox-root}") String sandboxRoot,
            @Value("${app.coding.approved-root}") String approvedRoot
    ) {
        this.workflowService = workflowService;
        this.patchGenerator = patchGenerator;
        this.sandboxPolicy = sandboxPolicy;
        this.projectTemplate = projectTemplate;
        this.buildRunner = buildRunner;
        this.diffRenderer = diffRenderer;
        this.sandboxRoot = configuredRoot(sandboxRoot, "沙箱");
        this.approvedRoot = configuredRoot(approvedRoot, "批准产物");
    }

    public CodingTask create(String workflowId) {
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
        AutonomyBudget budget = AutonomyBudget.safeDefault();
        List<CodingTaskEvent> events = new ArrayList<>();
        events.add(CodingTaskEvent.of("CREATED", "代码任务已创建，开始生成受限补丁"));
        CodingTask initial = new CodingTask(
                taskId, workflowId, CodingTaskStage.GENERATING, "", budget,
                0, 0, 0, List.of(), null, events,
                "", "", now, now
        );
        tasks.put(taskId, initial);

        String summary = "";
        List<PatchFile> generatedPatches = List.of();
        long generatedBytes = 0;
        int buildExecutions = 0;

        try {
            CodePatchPlan plan = sandboxPolicy.validate(
                    patchGenerator.generate(workflow), budget);
            summary = plan.summary();
            Path workspace = prepareTaskDirectory(sandboxRoot, taskId);
            taskWorkspaces.put(taskId, workspace);
            projectTemplate.initialize(workspace);
            generatedPatches = writeGeneratedFiles(workspace, plan.files());
            generatedBytes = generatedPatches.stream().mapToLong(PatchFile::bytes).sum();
            events.add(CodingTaskEvent.of(
                    "PATCH_GENERATED",
                    "生成并校验 " + generatedPatches.size() + " 个文件，共 "
                            + generatedBytes + " 字节"
            ));
            events.add(CodingTaskEvent.of(
                    "BUILD_STARTED",
                    "在无网络、只读根文件系统的Docker沙箱中执行一次Maven测试"
            ));
            buildExecutions = 1;
            BuildVerification verification = buildRunner.verify(workspace, budget);
            CodingTaskStage stage = verification.passed()
                    ? CodingTaskStage.WAITING_APPROVAL
                    : CodingTaskStage.FAILED;
            events.add(CodingTaskEvent.of(
                    verification.passed() ? "BUILD_PASSED" : "BUILD_FAILED",
                    verification.passed()
                            ? "自动化测试通过，等待人工批准产物"
                            : "自动化测试未通过，禁止发布产物"
            ));
            CodingTask completed = new CodingTask(
                    taskId, workflowId, stage, summary, budget,
                    generatedPatches.size(), generatedBytes, buildExecutions,
                    generatedPatches, verification,
                    events, "",
                    verification.passed() ? "" : "自动化测试未通过",
                    now, Instant.now()
            );
            tasks.put(taskId, completed);
            return completed;
        } catch (IOException exception) {
            return fail(initial, events, "沙箱文件操作失败", summary,
                    generatedPatches, generatedBytes, buildExecutions);
        } catch (CodingTaskException exception) {
            return fail(initial, events, exception.getMessage(), summary,
                    generatedPatches, generatedBytes, buildExecutions);
        } catch (RuntimeException exception) {
            return fail(initial, events, "代码任务执行失败", summary,
                    generatedPatches, generatedBytes, buildExecutions);
        }
    }

    public CodingTask get(String taskId) {
        CodingTask task = tasks.get(taskId);
        if (task == null) {
            throw new CodingTaskException("代码任务不存在：" + taskId);
        }
        return task;
    }

    public Optional<CodingTask> findByWorkflowId(String workflowId) {
        return tasks.values().stream()
                .filter(task -> task.workflowId().equals(workflowId))
                .max(Comparator.comparing(CodingTask::createdAt));
    }

    public CodingTask approve(String taskId, String comment) {
        CodingTask current = get(taskId);
        if (current.stage() != CodingTaskStage.WAITING_APPROVAL
                || current.verification() == null
                || !current.verification().passed()) {
            throw new CodingTaskException("只有测试通过并等待审批的代码任务可以发布");
        }
        Path workspace = taskWorkspaces.get(taskId);
        if (workspace == null) {
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
            List<CodingTaskEvent> events = new ArrayList<>(current.events());
            String normalizedComment = comment == null ? "" : comment.trim();
            events.add(CodingTaskEvent.of(
                    "PUBLISHED",
                    normalizedComment.isBlank()
                            ? "人工审批通过，产物已复制到批准目录"
                            : "人工审批通过：" + normalizedComment
            ));
            CodingTask published = new CodingTask(
                    current.taskId(), current.workflowId(), CodingTaskStage.PUBLISHED,
                    current.summary(), current.budget(), current.consumedFiles(),
                    current.consumedBytes(), current.consumedBuildExecutions(),
                    current.patches(), current.verification(), events,
                    output.toString(), "", current.createdAt(), Instant.now()
            );
            tasks.put(taskId, published);
            return published;
        } catch (IOException exception) {
            throw new CodingTaskException("批准产物写入失败", exception);
        }
    }

    private List<PatchFile> writeGeneratedFiles(
            Path workspace,
            List<GeneratedFile> generatedFiles
    ) throws IOException {
        List<PatchFile> patches = new ArrayList<>();
        for (GeneratedFile generated : generatedFiles) {
            Path target = sandboxPolicy.resolveContained(workspace, generated.relativePath());
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                throw new CodingTaskException("本阶段只允许新增文件：" + generated.relativePath());
            }
            byte[] bytes = generated.content().getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
            patches.add(new PatchFile(
                    generated.relativePath(), generated.purpose(), "ADD",
                    sha256(bytes), bytes.length,
                    diffRenderer.renderAddedFile(
                            generated.relativePath(), generated.content())
            ));
        }
        return List.copyOf(patches);
    }

    private CodingTask fail(
            CodingTask initial,
            List<CodingTaskEvent> events,
            String message,
            String summary,
            List<PatchFile> patches,
            long consumedBytes,
            int buildExecutions
    ) {
        List<CodingTaskEvent> updatedEvents = new ArrayList<>(events);
        updatedEvents.add(CodingTaskEvent.of("FAILED", message));
        CodingTask failed = new CodingTask(
                initial.taskId(), initial.workflowId(), CodingTaskStage.FAILED,
                summary, initial.budget(), patches.size(), consumedBytes,
                buildExecutions, patches, null, updatedEvents, "", message,
                initial.createdAt(), Instant.now()
        );
        tasks.put(failed.taskId(), failed);
        return failed;
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
