package com.gaozhaoyang.agent.solution;

import java.util.List;

public record SolutionGroundingReport(
        List<GroundedSolutionClaim> claims,
        int totalFactualClaims,
        int evidenceLinkedClaims,
        int assumptionClaims,
        int unsupportedClaims,
        double groundingRate,
        boolean evidenceSufficient,
        List<String> warnings
) {
    public SolutionGroundingReport {
        claims = claims == null ? List.of() : List.copyOf(claims);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static SolutionGroundingReport empty() {
        return new SolutionGroundingReport(
                List.of(), 0, 0, 0, 0, 0.0, false, List.of()
        );
    }
}
