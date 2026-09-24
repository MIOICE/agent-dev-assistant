package com.gaozhaoyang.agent.knowledge;

import java.util.List;

public record EvidenceQueryAttempt(
        int round,
        String needId,
        String query,
        List<String> resultIds,
        double topScore,
        boolean satisfied
) {

    public EvidenceQueryAttempt {
        if (round < 1) {
            throw new IllegalArgumentException("检索轮次必须大于0");
        }
        needId = needId == null ? "" : needId.trim();
        query = query == null ? "" : query.trim();
        resultIds = resultIds == null ? List.of() : List.copyOf(resultIds);
        topScore = Math.max(0.0, Math.min(topScore, 1.0));
    }
}
