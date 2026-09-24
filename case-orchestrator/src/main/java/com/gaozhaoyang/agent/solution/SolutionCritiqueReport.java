package com.gaozhaoyang.agent.solution;

import java.util.List;

public record SolutionCritiqueReport(
        String mode,
        List<ClaimEvidenceAssessment> assessments,
        int totalFactualClaims,
        int supportedClaims,
        int contradictedClaims,
        int insufficientClaims,
        int notEvaluatedClaims,
        int assumptionClaims,
        double supportRate,
        boolean safeForApproval,
        List<String> warnings
) {
    public SolutionCritiqueReport {
        mode = mode == null ? "" : mode.trim();
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static SolutionCritiqueReport empty() {
        return new SolutionCritiqueReport(
                "NOT_RUN", List.of(), 0, 0, 0, 0, 0, 0, 0.0, false, List.of()
        );
    }
}
