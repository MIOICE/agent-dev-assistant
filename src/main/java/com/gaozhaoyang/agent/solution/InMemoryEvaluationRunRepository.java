package com.gaozhaoyang.agent.solution;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "app.evaluation.repository", havingValue = "memory")
public class InMemoryEvaluationRunRepository implements EvaluationRunRepository {

    private final Map<String, ClaimEvidenceEvaluationRun> runs = new LinkedHashMap<>();

    @Override
    public synchronized ClaimEvidenceEvaluationRun save(ClaimEvidenceEvaluationRun run) {
        runs.put(run.runId(), run);
        return run;
    }

    @Override
    public synchronized List<ClaimEvidenceEvaluationRun> findAll(int limit) {
        return runs.values().stream()
                .sorted(Comparator.comparing(ClaimEvidenceEvaluationRun::createdAt).reversed())
                .limit(normalizeLimit(limit))
                .toList();
    }

    @Override
    public synchronized Optional<ClaimEvidenceEvaluationRun> findLatestPassing() {
        return new ArrayList<>(runs.values()).stream()
                .filter(run -> run.gate().status() == EvaluationGateStatus.PASSED)
                .max(Comparator.comparing(ClaimEvidenceEvaluationRun::createdAt));
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
