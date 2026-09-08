package com.gaozhaoyang.agent.coding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.coding.repository", havingValue = "memory")
public class InMemoryCodingTaskRepository implements CodingTaskRepository {

    private final Map<String, CodingTask> tasks = new ConcurrentHashMap<>();

    @Override
    public CodingTask save(CodingTask task) {
        tasks.put(task.taskId(), task);
        return task;
    }

    @Override
    public Optional<CodingTask> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public Optional<CodingTask> findLatestByWorkflowId(String workflowId) {
        return tasks.values().stream()
                .filter(task -> task.workflowId().equals(workflowId))
                .max(Comparator.comparing(CodingTask::createdAt));
    }

    @Override
    public List<CodingTask> findByStages(Set<CodingTaskStage> stages) {
        return tasks.values().stream()
                .filter(task -> stages.contains(task.stage()))
                .sorted(Comparator.comparing(CodingTask::updatedAt))
                .toList();
    }
}
