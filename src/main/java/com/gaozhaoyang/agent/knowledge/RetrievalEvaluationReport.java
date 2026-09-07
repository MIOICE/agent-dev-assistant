package com.gaozhaoyang.agent.knowledge;

import java.util.List;

public record RetrievalEvaluationReport(
        double similarityThreshold,
        int topK,
        int totalCases,
        int positiveCases,
        int negativeCases,
        double hitAtK,
        double precisionAtK,
        double meanReciprocalRank,
        double irrelevantRejectionRate,
        List<CaseResult> cases
) {

    public record CaseResult(
            String id,
            String query,
            List<String> expectedSourceIds,
            List<String> retrievedSourceIds,
            boolean hit,
            double precisionAtK,
            double reciprocalRank,
            boolean rejected,
            boolean passed
    ) {
    }
}
