package com.gaozhaoyang.agent.coding;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CodingTaskRepository {

    CodingTask save(CodingTask task);

    Optional<CodingTask> findById(String taskId);

    Optional<CodingTask> findLatestByWorkflowId(String workflowId);

    List<CodingTask> findByStages(Set<CodingTaskStage> stages);
}
