package com.gaozhaoyang.agent.solution;

import java.util.List;
import java.util.Optional;

public interface EvaluationRunRepository {

    ClaimEvidenceEvaluationRun save(ClaimEvidenceEvaluationRun run);

    List<ClaimEvidenceEvaluationRun> findAll(int limit);

    Optional<ClaimEvidenceEvaluationRun> findLatestPassing();
}
