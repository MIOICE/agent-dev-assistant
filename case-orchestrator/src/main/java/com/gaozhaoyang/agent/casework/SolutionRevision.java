package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.time.Instant;

public record SolutionRevision(
        int revision,
        TechnicalSolution solution,
        String changedBy,
        String changeReason,
        Instant createdAt,
        String promptVersion,
        String skillManifestHash
) {
    public SolutionRevision(int revision, TechnicalSolution solution, String changedBy,
                            String changeReason, Instant createdAt) {
        this(revision, solution, changedBy, changeReason, createdAt,
                "manual-review", "not-applicable");
    }
}
