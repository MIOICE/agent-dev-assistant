package com.gaozhaoyang.agent.solution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
@ConditionalOnProperty(
        name = "app.evaluation.repository",
        havingValue = "file",
        matchIfMissing = true
)
public class FileEvaluationRunRepository implements EvaluationRunRepository {

    private final ObjectMapper objectMapper;
    private final Path checkpointRoot;

    public FileEvaluationRunRepository(
            ObjectMapper objectMapper,
            @Value("${app.evaluation.checkpoint-root}") String checkpointRoot
    ) {
        this.objectMapper = objectMapper;
        this.checkpointRoot = configuredRoot(checkpointRoot);
        initializeRoot();
    }

    @Override
    public synchronized ClaimEvidenceEvaluationRun save(ClaimEvidenceEvaluationRun run) {
        Path target = runFile(run.runId());
        Path temporary = checkpointRoot.resolve(
                "." + run.runId() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(
                    temporary,
                    objectMapper.writeValueAsString(run),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return run;
        } catch (Exception exception) {
            throw new EvaluationPersistenceException(
                    "保存评测运行失败：" + run.runId(), exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件由后续运维清理，不扩大删除范围。
            }
        }
    }

    @Override
    public synchronized List<ClaimEvidenceEvaluationRun> findAll(int limit) {
        return readAll().stream()
                .sorted(Comparator.comparing(ClaimEvidenceEvaluationRun::createdAt).reversed())
                .limit(normalizeLimit(limit))
                .toList();
    }

    @Override
    public synchronized Optional<ClaimEvidenceEvaluationRun> findLatestPassing() {
        return readAll().stream()
                .filter(run -> run.gate().status() == EvaluationGateStatus.PASSED)
                .max(Comparator.comparing(ClaimEvidenceEvaluationRun::createdAt));
    }

    private List<ClaimEvidenceEvaluationRun> readAll() {
        try (Stream<Path> files = Files.list(checkpointRoot)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(this::read)
                    .toList();
        } catch (IOException exception) {
            throw new EvaluationPersistenceException("读取评测运行列表失败", exception);
        }
    }

    private ClaimEvidenceEvaluationRun read(Path file) {
        try {
            return objectMapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    ClaimEvidenceEvaluationRun.class
            );
        } catch (Exception exception) {
            throw new EvaluationPersistenceException(
                    "读取评测运行失败：" + file.getFileName(), exception);
        }
    }

    private Path runFile(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("评测运行ID格式不合法");
        }
        Path file = checkpointRoot.resolve(runId + ".json").normalize();
        if (!file.startsWith(checkpointRoot)) {
            throw new IllegalArgumentException("评测Checkpoint路径越界");
        }
        return file;
    }

    private Path configuredRoot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("评测Checkpoint目录不能为空");
        }
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (root.getNameCount() < 2) {
            throw new IllegalArgumentException("评测Checkpoint目录范围过大");
        }
        return root;
    }

    private void initializeRoot() {
        try {
            Files.createDirectories(checkpointRoot);
            if (Files.isSymbolicLink(checkpointRoot)) {
                throw new IllegalArgumentException("评测Checkpoint目录不能是符号链接");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法创建评测Checkpoint目录", exception);
        }
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
