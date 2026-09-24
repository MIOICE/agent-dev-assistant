package com.gaozhaoyang.agent.solution;

public record EvaluationMetricSnapshot(
        double coverage,
        double accuracy,
        double supportedRecall,
        double contradictedRecall,
        double insufficientRecall,
        double macroRecall
) {
    public static EvaluationMetricSnapshot from(ClaimEvidenceEvaluationReport report) {
        return new EvaluationMetricSnapshot(
                report.coverage(),
                report.accuracy(),
                report.supportedRecall(),
                report.contradictedRecall(),
                report.insufficientRecall(),
                report.macroRecall()
        );
    }

    public double minimumClassRecall() {
        return Math.min(supportedRecall, Math.min(contradictedRecall, insufficientRecall));
    }

    public EvaluationMetricSnapshot minus(EvaluationMetricSnapshot baseline) {
        return new EvaluationMetricSnapshot(
                round(coverage - baseline.coverage),
                round(accuracy - baseline.accuracy),
                round(supportedRecall - baseline.supportedRecall),
                round(contradictedRecall - baseline.contradictedRecall),
                round(insufficientRecall - baseline.insufficientRecall),
                round(macroRecall - baseline.macroRecall)
        );
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
