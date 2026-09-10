package com.gaozhaoyang.agent.solution;

import java.util.List;

public record CritiqueBatch(
        List<ClaimEvidenceAssessment> assessments
) {
    public CritiqueBatch {
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
    }
}
