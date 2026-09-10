package com.gaozhaoyang.agent.solution;

import java.util.List;

public record ClaimEvidenceAssessment(
        String claimId,
        ClaimEvidenceVerdict verdict,
        double confidence,
        String rationale,
        List<String> evidenceIds
) {
    public ClaimEvidenceAssessment {
        claimId = claimId == null ? "" : claimId.trim();
        verdict = verdict == null ? ClaimEvidenceVerdict.NOT_EVALUATED : verdict;
        confidence = Double.isFinite(confidence)
                ? Math.max(0.0, Math.min(1.0, confidence))
                : 0.0;
        rationale = rationale == null ? "" : rationale.trim();
        if (rationale.length() > 500) {
            rationale = rationale.substring(0, 500);
        }
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
