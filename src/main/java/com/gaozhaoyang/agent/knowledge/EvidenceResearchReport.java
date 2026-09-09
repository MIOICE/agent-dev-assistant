package com.gaozhaoyang.agent.knowledge;

import java.util.List;

public record EvidenceResearchReport(
        String planningMode,
        List<EvidenceNeed> plannedNeeds,
        List<EvidenceQueryAttempt> attempts,
        List<KnowledgeSearchResult> evidence,
        List<String> unresolvedNeedIds,
        boolean sufficient,
        int roundsUsed,
        int queriesUsed,
        int maxRounds,
        int maxQueries
) {

    public EvidenceResearchReport {
        planningMode = planningMode == null ? "UNKNOWN" : planningMode;
        plannedNeeds = plannedNeeds == null ? List.of() : List.copyOf(plannedNeeds);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        unresolvedNeedIds = unresolvedNeedIds == null
                ? List.of()
                : List.copyOf(unresolvedNeedIds);
        roundsUsed = Math.max(roundsUsed, 0);
        queriesUsed = Math.max(queriesUsed, 0);
        maxRounds = Math.max(maxRounds, 1);
        maxQueries = Math.max(maxQueries, 1);
    }

    public static EvidenceResearchReport empty() {
        return new EvidenceResearchReport(
                "NOT_RUN", List.of(), List.of(), List.of(), List.of(),
                false, 0, 0, 1, 1
        );
    }
}
