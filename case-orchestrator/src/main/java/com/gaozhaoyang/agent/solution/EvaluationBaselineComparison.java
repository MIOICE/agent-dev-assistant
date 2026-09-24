package com.gaozhaoyang.agent.solution;

import java.util.List;

public record EvaluationBaselineComparison(
        boolean baselineAvailable,
        String baselineRunId,
        EvaluationMetricSnapshot baseline,
        EvaluationMetricSnapshot deltas,
        boolean regressionDetected,
        List<String> regressions
) {
    public EvaluationBaselineComparison {
        regressions = regressions == null ? List.of() : List.copyOf(regressions);
    }

    public static EvaluationBaselineComparison unavailable() {
        return new EvaluationBaselineComparison(false, null, null, null, false, List.of());
    }
}
