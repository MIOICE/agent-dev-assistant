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
        Map<String, Map<String, Integer>> confusionMatrix,
        List<CaseResult> cases,
        List<String> warnings,
        Instant evaluatedAt
) {
    public ClaimEvidenceEvaluationReport {
        confusionMatrix = confusionMatrix == null ? Map.of() : Map.copyOf(confusionMatrix);
        cases = cases == null ? List.of() : List.copyOf(cases);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    public record CaseResult(
            String id,
            String claim,
            List<String> tags,
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
