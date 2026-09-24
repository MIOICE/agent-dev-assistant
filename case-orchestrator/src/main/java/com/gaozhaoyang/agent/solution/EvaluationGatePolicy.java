package com.gaozhaoyang.agent.solution;

import java.util.ArrayList;
import java.util.List;

public class EvaluationGatePolicy {

    private final EvaluationThresholds thresholds;

    public EvaluationGatePolicy(EvaluationThresholds thresholds) {
        this.thresholds = thresholds;
    }

    public EvaluationBaselineComparison compare(
            ClaimEvidenceEvaluationReport currentReport,
            ClaimEvidenceEvaluationRun baselineRun
    ) {
        if (baselineRun == null) {
            return EvaluationBaselineComparison.unavailable();
        }
        EvaluationMetricSnapshot current = EvaluationMetricSnapshot.from(currentReport);
        EvaluationMetricSnapshot baseline = EvaluationMetricSnapshot.from(baselineRun.report());
        EvaluationMetricSnapshot deltas = current.minus(baseline);
        List<String> regressions = new ArrayList<>();
        addRegression(regressions, "Coverage", deltas.coverage());
        addRegression(regressions, "Accuracy", deltas.accuracy());
        addRegression(regressions, "SUPPORTED Recall", deltas.supportedRecall());
        addRegression(regressions, "CONTRADICTED Recall", deltas.contradictedRecall());
        addRegression(regressions, "INSUFFICIENT Recall", deltas.insufficientRecall());
        addRegression(regressions, "Macro Recall", deltas.macroRecall());
        return new EvaluationBaselineComparison(
                true,
                baselineRun.runId(),
                baseline,
                deltas,
                !regressions.isEmpty(),
                regressions
        );
    }

    public EvaluationGateDecision decide(
            ClaimEvidenceEvaluationReport report,
            EvaluationBaselineComparison comparison
    ) {
        if (report.evaluatedCases() == 0) {
            return new EvaluationGateDecision(
                    EvaluationGateStatus.NOT_EVALUATED,
                    false,
                    thresholds,
                    List.of("当前模式没有执行模型语义判定，不能参与发布门禁。")
            );
        }
        EvaluationMetricSnapshot metrics = EvaluationMetricSnapshot.from(report);
        List<String> failures = new ArrayList<>();
        addMinimumFailure(failures, "Coverage", metrics.coverage(), thresholds.minimumCoverage());
        addMinimumFailure(failures, "Accuracy", metrics.accuracy(), thresholds.minimumAccuracy());
        addMinimumFailure(
                failures, "Macro Recall", metrics.macroRecall(), thresholds.minimumMacroRecall());
        addMinimumFailure(
                failures,
                "最低分类 Recall",
                metrics.minimumClassRecall(),
                thresholds.minimumClassRecall()
        );
        if (comparison.regressionDetected()) {
            failures.addAll(comparison.regressions());
        }
        EvaluationGateStatus status = failures.isEmpty()
                ? EvaluationGateStatus.PASSED
                : EvaluationGateStatus.FAILED;
        return new EvaluationGateDecision(
                status,
                status == EvaluationGateStatus.PASSED,
                thresholds,
                failures.isEmpty() ? List.of("全部绝对阈值与基线回归检查通过。") : failures
        );
    }

    private void addMinimumFailure(
            List<String> failures,
            String metric,
            double actual,
            double minimum
    ) {
        if (actual < minimum) {
            failures.add("%s %.2f%% 低于门禁 %.2f%%。".formatted(
                    metric, actual * 100.0, minimum * 100.0));
        }
    }

    private void addRegression(List<String> regressions, String metric, double delta) {
        if (delta < -thresholds.maximumRegression()) {
            regressions.add("%s 相对通过基线下降 %.2f 个百分点，超过允许值 %.2f。".formatted(
                    metric,
                    -delta * 100.0,
                    thresholds.maximumRegression() * 100.0
            ));
        }
    }
}
