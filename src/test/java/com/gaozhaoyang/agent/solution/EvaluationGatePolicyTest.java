package com.gaozhaoyang.agent.solution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationGatePolicyTest {

    private final EvaluationThresholds thresholds =
            new EvaluationThresholds(0.95, 0.85, 0.80, 0.75, 0.08);
    private final EvaluationGatePolicy policy = new EvaluationGatePolicy(thresholds);

    @Test
    void shouldPassMetricsAboveAbsoluteThresholdsWithoutBaseline() {
        ClaimEvidenceEvaluationReport report = report(1.0, 0.92, 1.0, 0.75, 1.0, 0.9167, 12);

        EvaluationBaselineComparison comparison = policy.compare(report, null);
        EvaluationGateDecision decision = policy.decide(report, comparison);

        assertThat(comparison.baselineAvailable()).isFalse();
        assertThat(decision.status()).isEqualTo(EvaluationGateStatus.PASSED);
        assertThat(decision.publishable()).isTrue();
    }

    @Test
    void shouldFailWhenAnyAbsoluteThresholdIsMissed() {
        ClaimEvidenceEvaluationReport report = report(1.0, 0.92, 1.0, 0.50, 1.0, 0.8333, 12);

        EvaluationGateDecision decision = policy.decide(report, policy.compare(report, null));

        assertThat(decision.status()).isEqualTo(EvaluationGateStatus.FAILED);
        assertThat(decision.reasons()).anyMatch(reason -> reason.contains("最低分类 Recall"));
    }

    @Test
    void shouldKeepMockRunOutsideReleaseGate() {
        ClaimEvidenceEvaluationReport report = report(0, 0, 0, 0, 0, 0, 0);

        EvaluationGateDecision decision = policy.decide(report, policy.compare(report, null));

        assertThat(decision.status()).isEqualTo(EvaluationGateStatus.NOT_EVALUATED);
        assertThat(decision.publishable()).isFalse();
    }

    @Test
    void shouldFailWhenMetricRegressesAgainstLastPassingBaseline() {
        ClaimEvidenceEvaluationReport baselineReport =
                report(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 12);
        ClaimEvidenceEvaluationRun baseline = run("baseline", baselineReport, EvaluationGateStatus.PASSED);
        ClaimEvidenceEvaluationReport current =
                report(1.0, 0.91, 1.0, 0.75, 1.0, 0.9167, 12);

        EvaluationBaselineComparison comparison = policy.compare(current, baseline);
        EvaluationGateDecision decision = policy.decide(current, comparison);

        assertThat(comparison.regressionDetected()).isTrue();
        assertThat(comparison.deltas().accuracy()).isEqualTo(-0.09);
        assertThat(decision.status()).isEqualTo(EvaluationGateStatus.FAILED);
        assertThat(decision.reasons()).anyMatch(reason -> reason.contains("Accuracy"));
    }

    static ClaimEvidenceEvaluationReport report(
            double coverage,
            double accuracy,
            double supportedRecall,
            double contradictedRecall,
            double insufficientRecall,
            double macroRecall,
            int evaluatedCases
    ) {
        return new ClaimEvidenceEvaluationReport(
                evaluatedCases == 0 ? "RULE_BASED_NOT_EVALUATED" : "MODEL",
                12,
                evaluatedCases,
                (int) Math.round(accuracy * evaluatedCases),
                coverage,
                accuracy,
                supportedRecall,
                contradictedRecall,
                insufficientRecall,
                macroRecall,
                Map.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-09-10T00:00:00Z")
        );
    }

    static ClaimEvidenceEvaluationRun run(
            String runId,
            ClaimEvidenceEvaluationReport report,
            EvaluationGateStatus status
    ) {
        EvaluationThresholds thresholds =
                new EvaluationThresholds(0.95, 0.85, 0.8, 0.75, 0.08);
        return new ClaimEvidenceEvaluationRun(
                runId,
                "deepseek",
                "deepseek-test",
                "prompt-v1",
                "dataset-v1",
                "a".repeat(64),
                report,
                new EvaluationGateDecision(
                        status,
                        status == EvaluationGateStatus.PASSED,
                        thresholds,
                        List.of()
                ),
                EvaluationBaselineComparison.unavailable(),
                report.evaluatedAt()
        );
    }
}
