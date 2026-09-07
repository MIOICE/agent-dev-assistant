package com.gaozhaoyang.agent.workflow;

import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(
        name = "app.workflow.repository",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryWorkflowRepository implements WorkflowRepository {

    private final Map<String, WorkflowState> states = new ConcurrentHashMap<>();

    @Override
    public WorkflowState save(WorkflowState state) {
        states.put(state.workflowId(), state);
        return state;
    }

    @Override
    public Optional<WorkflowState> findById(String workflowId) {
        return Optional.ofNullable(states.get(workflowId));
    }

    @Override
    public List<WorkflowState> findAll(WorkflowStage stage, int offset, int limit) {
        return states.values().stream()
                .filter(state -> stage == null || state.stage() == stage)
                .sorted(Comparator.comparing(WorkflowState::updatedAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long count(WorkflowStage stage) {
        return states.values().stream()
                .filter(state -> stage == null || state.stage() == stage)
                .count();
    }
}
