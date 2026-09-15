package com.gaozhaoyang.agent.solution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ClaimEvidenceEvaluationReport(
        String mode,
        int totalCases,
        int evaluatedCases,
        int correctCases,
        double coverage,
        double accuracy,
        double supportedRecall,
        double contradictedRecall,
        double insufficientRecall,
        double macroRecall,
        Map<String, SegmentMetrics> difficultyMetrics,
        Map<String, Map<String, Integer>> confusionMatrix,
        List<CaseResult> cases,
        List<String> warnings,
        Instant evaluatedAt
) {
    public ClaimEvidenceEvaluationReport {
        difficultyMetrics = difficultyMetrics == null ? Map.of() : Map.copyOf(difficultyMetrics);
        confusionMatrix = confusionMatrix == null ? Map.of() : Map.copyOf(confusionMatrix);
        cases = cases == null ? List.of() : List.copyOf(cases);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    public ClaimEvidenceEvaluationReport(
            String mode,
            int totalCases,
            int evaluatedCases,
            int correctCases,
            double coverage,
            double accuracy,
            double supportedRecall,
            double contradictedRecall,
            double insufficientRecall,
            double macroRecall,
            Map<String, Map<String, Integer>> confusionMatrix,
            List<CaseResult> cases,
            List<String> warnings,
            Instant evaluatedAt
    ) {
        this(mode, totalCases, evaluatedCases, correctCases, coverage, accuracy,
                supportedRecall, contradictedRecall, insufficientRecall, macroRecall,
                Map.of(), confusionMatrix, cases, warnings, evaluatedAt);
    }

    public record SegmentMetrics(
            int totalCases,
            int evaluatedCases,
            int correctCases,
            double coverage,
            double accuracy
    ) {
    }

    public record CaseResult(
            String id,
            String claim,
            List<String> tags,
            String difficulty,
            ClaimEvidenceVerdict expectedVerdict,
            ClaimEvidenceVerdict actualVerdict,
            double confidence,
            boolean evaluated,
            boolean correct,
            String rationale
    ) {
        public CaseResult {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
