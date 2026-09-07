package com.gaozhaoyang.agent.workflow;

import java.util.List;
import java.util.Optional;

public interface WorkflowRepository {

    WorkflowState save(WorkflowState state);

    Optional<WorkflowState> findById(String workflowId);

    List<WorkflowState> findAll(WorkflowStage stage, int offset, int limit);

    long count(WorkflowStage stage);
}
