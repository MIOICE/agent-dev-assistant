package com.gaozhaoyang.agent.solution;

import java.util.List;

public record GroundedSolutionClaim(
        String claimId,
        String section,
        int itemIndex,
        String claim,
        List<String> evidenceIds,
        ClaimGroundingStatus status,
        String explanation
) {
    public GroundedSolutionClaim {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
