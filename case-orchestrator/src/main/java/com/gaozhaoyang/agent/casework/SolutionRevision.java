package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.time.Instant;

public record SolutionRevision(
        int revision,
        TechnicalSolution solution,
        String changedBy,
        String changeReason,
        Instant createdAt
) {
}
