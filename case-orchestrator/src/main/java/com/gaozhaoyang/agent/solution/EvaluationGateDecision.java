package com.gaozhaoyang.agent.solution;

import java.util.List;

public record EvaluationGateDecision(
        EvaluationGateStatus status,
        boolean publishable,
        EvaluationThresholds thresholds,
        List<String> reasons
) {
    public EvaluationGateDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
