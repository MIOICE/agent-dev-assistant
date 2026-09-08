package com.gaozhaoyang.agent.coding;

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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
@ConditionalOnProperty(
        name = "app.coding.repository",
        havingValue = "file",
        matchIfMissing = true
)
public class FileCodingTaskRepository implements CodingTaskRepository {

    private final ObjectMapper objectMapper;
    private final Path checkpointRoot;

    public FileCodingTaskRepository(
            ObjectMapper objectMapper,
            @Value("${app.coding.checkpoint-root}") String checkpointRoot
    ) {
        this.objectMapper = objectMapper;
        this.checkpointRoot = configuredRoot(checkpointRoot);
        initializeRoot();
    }

    @Override
    public synchronized CodingTask save(CodingTask task) {
        Path target = checkpointFile(task.taskId());
        Path temporary = checkpointRoot.resolve(
                "." + task.taskId() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(
                    temporary,
                    objectMapper.writeValueAsString(task),
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
            return task;
        } catch (Exception exception) {
            throw new CodingTaskException("保存代码任务Checkpoint失败：" + task.taskId(), exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 临时文件会被下一次运维清理，不扩大删除范围。
            }
        }
    }

    @Override
    public synchronized Optional<CodingTask> findById(String taskId) {
        Path file = checkpointFile(taskId);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file));
    }

    @Override
    public synchronized Optional<CodingTask> findLatestByWorkflowId(String workflowId) {
        return readAll().stream()
                .filter(task -> task.workflowId().equals(workflowId))
                .max(Comparator.comparing(CodingTask::createdAt));
    }

    @Override
    public synchronized List<CodingTask> findByStages(Set<CodingTaskStage> stages) {
        return readAll().stream()
                .filter(task -> stages.contains(task.stage()))
                .sorted(Comparator.comparing(CodingTask::updatedAt))
                .toList();
    }

    private List<CodingTask> readAll() {
        try (Stream<Path> files = Files.list(checkpointRoot)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(this::read)
                    .toList();
        } catch (IOException exception) {
            throw new CodingTaskException("读取代码任务Checkpoint列表失败", exception);
        }
    }

    private CodingTask read(Path file) {
        try {
            return objectMapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    CodingTask.class
            );
        } catch (Exception exception) {
            throw new CodingTaskException("读取代码任务Checkpoint失败：" + file.getFileName(), exception);
        }
    }

    private Path checkpointFile(String taskId) {
        if (taskId == null || !taskId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new CodingTaskException("代码任务ID格式不合法");
        }
        Path file = checkpointRoot.resolve(taskId + ".json").normalize();
        if (!file.startsWith(checkpointRoot)) {
            throw new CodingTaskException("Checkpoint路径越界");
        }
        return file;
    }

    private Path configuredRoot(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Checkpoint目录不能为空");
        }
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (root.getNameCount() < 2) {
            throw new IllegalArgumentException("Checkpoint目录范围过大");
        }
        return root;
    }

    private void initializeRoot() {
        try {
            Files.createDirectories(checkpointRoot);
            if (Files.isSymbolicLink(checkpointRoot)) {
                throw new IllegalArgumentException("Checkpoint目录不能是符号链接");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法创建Checkpoint目录", exception);
        }
    }
}
