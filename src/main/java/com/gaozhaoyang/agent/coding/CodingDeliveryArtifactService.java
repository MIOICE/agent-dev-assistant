package com.gaozhaoyang.agent.coding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CodingDeliveryArtifactService {

    private static final long MAX_SOURCE_BYTES = 2_000_000;
    private static final String MANIFEST_VERSION = "agent-delivery-v1";

    private final CodingTaskService codingTaskService;
    private final SandboxPolicy sandboxPolicy;
    private final SandboxProjectTemplate projectTemplate;
    private final ObjectMapper objectMapper;
    private final Path approvedRoot;

    public CodingDeliveryArtifactService(
            CodingTaskService codingTaskService,
            SandboxPolicy sandboxPolicy,
            SandboxProjectTemplate projectTemplate,
            ObjectMapper objectMapper,
            @Value("${app.coding.approved-root}") String approvedRoot
    ) {
        this.codingTaskService = codingTaskService;
        this.sandboxPolicy = sandboxPolicy;
        this.projectTemplate = projectTemplate;
        this.objectMapper = objectMapper;
        this.approvedRoot = configuredRoot(approvedRoot);
    }

    public DeliveryArtifactBundle build(String taskId) {
        CodingTask task = codingTaskService.get(taskId);
        validatePublishedTask(task);

        Path expectedOutput = sandboxPolicy.resolveContained(approvedRoot, task.taskId());
        Path actualOutput = Path.of(task.approvedOutputPath()).toAbsolutePath().normalize();
        if (!actualOutput.equals(expectedOutput) || !Files.isDirectory(actualOutput)) {
            throw new CodingTaskException("批准产物目录与任务Checkpoint不一致");
        }

        try {
            Map<String, byte[]> zipEntries = new TreeMap<>();
            List<DeliveryArtifactManifest.FileEntry> files = new ArrayList<>();
            long sourceBytes = 0;

            sourceBytes += addProjectFile(
                    actualOutput, "pom.xml", "BUILD_DESCRIPTOR", null,
                    zipEntries, files, sourceBytes);

            List<PatchFile> patches = task.patches().stream()
                    .sorted(Comparator.comparing(PatchFile::relativePath))
                    .toList();
            for (PatchFile patch : patches) {
                sourceBytes += addProjectFile(
                        actualOutput, patch.relativePath(), patch.operation(), patch,
                        zipEntries, files, sourceBytes);
            }

            ByteArrayOutputStream diffOutput = new ByteArrayOutputStream();
            for (PatchFile patch : patches) {
                String diff = patch.unifiedDiff() == null ? "" : patch.unifiedDiff();
                if (diff.length() > MAX_SOURCE_BYTES) {
                    throw new CodingTaskException("交付包Diff超过安全上限");
                }
                byte[] diffBytes = (diff + "\n").getBytes(StandardCharsets.UTF_8);
                if (sourceBytes + diffOutput.size() + diffBytes.length > MAX_SOURCE_BYTES) {
                    throw new CodingTaskException("交付包源内容超过安全上限");
                }
                diffOutput.write(diffBytes);
            }
            byte[] combinedDiff = diffOutput.toByteArray();
            String outputSummary = task.verification().outputSummary() == null
                    ? ""
                    : task.verification().outputSummary();
            if (outputSummary.length() > MAX_SOURCE_BYTES) {
                throw new CodingTaskException("构建摘要超过安全上限");
            }
            byte[] buildSummary = outputSummary.getBytes(StandardCharsets.UTF_8);
            sourceBytes += combinedDiff.length + buildSummary.length;
            if (sourceBytes > MAX_SOURCE_BYTES) {
                throw new CodingTaskException("交付包源内容超过安全上限");
            }
            zipEntries.put("changes/approved.patch", combinedDiff);
            zipEntries.put("evidence/build-summary.txt", buildSummary);

            DeliveryArtifactManifest manifest = manifest(task, files, buildSummary);
            byte[] manifestJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(manifest);
            zipEntries.put("delivery-manifest.json", manifestJson);
            zipEntries.put("APPLYING.md", applyingGuide(task).getBytes(StandardCharsets.UTF_8));

            byte[] zip = deterministicZip(zipEntries);
            return new DeliveryArtifactBundle(
                    "coding-delivery-" + task.taskId() + ".zip",
                    zip,
                    sha256(zip),
                    manifest
            );
        } catch (IOException exception) {
            throw new CodingTaskException("构建可验证交付包失败", exception);
        }
    }

    private void validatePublishedTask(CodingTask task) {
        if (task.stage() != CodingTaskStage.PUBLISHED) {
            throw new CodingTaskException("只有人工审批并发布的代码任务可以下载交付包");
        }
        if (task.approvedOutputPath().isBlank()
                || task.verification() == null
                || !task.verification().passed()) {
            throw new CodingTaskException("代码任务缺少已通过的构建证据或批准产物");
        }
    }

    private long addProjectFile(
            Path taskRoot,
            String relativePath,
            String operation,
            PatchFile expectedPatch,
            Map<String, byte[]> entries,
            List<DeliveryArtifactManifest.FileEntry> files,
            long consumedBytes
    ) throws IOException {
        Path source = sandboxPolicy.resolveContained(taskRoot, relativePath);
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new CodingTaskException("交付文件不存在或不是普通文件：" + relativePath);
        }
        long fileSize = Files.size(source);
        if (fileSize > MAX_SOURCE_BYTES - consumedBytes) {
            throw new CodingTaskException("交付包源内容超过安全上限");
        }
        byte[] content = expectedPatch == null && relativePath.equals("pom.xml")
                ? projectTemplate.readVerifiedBuildDescriptor(source)
                : Files.readAllBytes(source);
        String digest = sha256(content);
        if (expectedPatch != null
                && (!digest.equals(expectedPatch.sha256())
                    || content.length != expectedPatch.bytes())) {
            throw new CodingTaskException("批准产物完整性校验失败：" + relativePath);
        }
        String normalizedPath = relativePath.replace('\\', '/');
        entries.put("project/" + normalizedPath, content);
        files.add(new DeliveryArtifactManifest.FileEntry(
                normalizedPath, operation, content.length, digest));
        return content.length;
    }

    private DeliveryArtifactManifest manifest(
            CodingTask task,
            List<DeliveryArtifactManifest.FileEntry> files,
            byte[] buildSummary
    ) {
        BuildVerification verification = task.verification();
        int revision = task.workflowSnapshot() == null ? 0 : task.workflowSnapshot().revision();
        return new DeliveryArtifactManifest(
                MANIFEST_VERSION,
                task.taskId(),
                task.workflowId(),
                revision,
                task.updatedAt().toString(),
                task.summary(),
                new DeliveryArtifactManifest.Verification(
                        verification.passed(),
                        verification.command(),
                        verification.exitCode(),
                        verification.durationMs(),
                        sha256(buildSummary)
                ),
                task.budget(),
                task.agentLoop().consumedSteps(),
                task.consumedBuildExecutions(),
                task.repairAttempts(),
                task.activatedSkills(),
                files
        );
    }

    private byte[] deterministicZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private String applyingGuide(CodingTask task) {
        return """
                # 可验证代码交付包

                此交付包来自已通过沙箱构建并完成人工审批的 Coding Agent 任务。

                - taskId: %s
                - workflowId: %s
                - 文件清单与 SHA-256: `delivery-manifest.json`
                - 合并 Diff: `changes/approved.patch`
                - 构建证据: `evidence/build-summary.txt`
                - 独立工程: `project/`

                在合入真实仓库前，仍需由维护者复核 Diff、在目标仓库重新运行测试并遵循正式发布流程。
                """.formatted(task.taskId(), task.workflowId());
    }

    private Path configuredRoot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("批准产物目录不能为空");
        }
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (root.getNameCount() < 2) {
            throw new IllegalArgumentException("批准产物目录范围过大");
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
