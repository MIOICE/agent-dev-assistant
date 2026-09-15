package com.gaozhaoyang.agent.knowledge;

import java.util.List;
import java.util.Map;

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
        Map<String, SegmentMetrics> categoryMetrics,
        Map<String, SegmentMetrics> difficultyMetrics,
        List<CaseResult> cases
) {

    public record SegmentMetrics(
            int totalCases,
            int positiveCases,
            int negativeCases,
            double passRate,
            double hitAtK,
            double meanReciprocalRank,
            double irrelevantRejectionRate
    ) {
    }

    public record CaseResult(
            String id,
            String query,
            String category,
            String difficulty,
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
