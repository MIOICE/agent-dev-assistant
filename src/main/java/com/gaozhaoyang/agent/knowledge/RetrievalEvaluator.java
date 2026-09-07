package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetrievalEvaluator {

    private final ConfigurableKnowledgeSearcher knowledgeSearcher;
    private final RetrievalEvaluationDataset dataset;
    private final double currentThreshold;
    private final int topK;
    private final List<Double> comparisonThresholds;

    @Autowired
    public RetrievalEvaluator(
            ConfigurableKnowledgeSearcher knowledgeSearcher,
            RetrievalEvaluationDataset dataset,
            @Value("${app.embedding.similarity-threshold:0.45}")
            double currentThreshold,
            @Value("${app.embedding.top-k:4}") int topK,
            @Value("${app.embedding.evaluation-thresholds:0.40,0.45,0.50,0.55}")
            String comparisonThresholds
    ) {
        this.knowledgeSearcher = knowledgeSearcher;
        this.dataset = dataset;
        this.currentThreshold = currentThreshold;
        this.topK = topK;
        this.comparisonThresholds = parseThresholds(comparisonThresholds);
    }

    RetrievalEvaluator(
            ConfigurableKnowledgeSearcher knowledgeSearcher,
            List<RetrievalEvaluationCase> cases,
            double currentThreshold,
            int topK,
            List<Double> comparisonThresholds
    ) {
        this.knowledgeSearcher = knowledgeSearcher;
        this.dataset = new RetrievalEvaluationDataset(cases);
        this.currentThreshold = currentThreshold;
        this.topK = topK;
        this.comparisonThresholds = List.copyOf(comparisonThresholds);
    }

    public RetrievalEvaluationReport evaluate() {
        return evaluate(dataset.findAll(), currentThreshold);
    }

    public List<RetrievalEvaluationReport> compareThresholds() {
        return comparisonThresholds.stream()
                .map(threshold -> evaluate(dataset.findAll(), threshold))
                .toList();
    }

    RetrievalEvaluationReport evaluate(
            List<RetrievalEvaluationCase> dataset
    ) {
        return evaluate(dataset, currentThreshold);
    }

    RetrievalEvaluationReport evaluate(
            List<RetrievalEvaluationCase> dataset,
            double similarityThreshold
    ) {
        List<RetrievalEvaluationReport.CaseResult> caseResults = dataset
                .stream()
                .map(evaluationCase -> evaluateCase(
                        evaluationCase,
                        similarityThreshold
                ))
                .toList();

        List<RetrievalEvaluationReport.CaseResult> positiveResults =
                caseResults.stream()
                        .filter(result -> !result.expectedSourceIds().isEmpty())
                        .toList();
        List<RetrievalEvaluationReport.CaseResult> negativeResults =
                caseResults.stream()
                        .filter(result -> result.expectedSourceIds().isEmpty())
                        .toList();

        return new RetrievalEvaluationReport(
                similarityThreshold,
                topK,
                caseResults.size(),
                positiveResults.size(),
                negativeResults.size(),
                average(positiveResults.stream()
                        .map(result -> result.hit() ? 1.0 : 0.0)
                        .toList()),
                average(positiveResults.stream()
                        .map(RetrievalEvaluationReport.CaseResult::precisionAtK)
                        .toList()),
                average(positiveResults.stream()
                        .map(RetrievalEvaluationReport.CaseResult::reciprocalRank)
                        .toList()),
                average(negativeResults.stream()
                        .map(result -> result.rejected() ? 1.0 : 0.0)
                        .toList()),
                caseResults
        );
    }

    private RetrievalEvaluationReport.CaseResult evaluateCase(
            RetrievalEvaluationCase evaluationCase,
            double similarityThreshold
    ) {
        List<Document> retrievedDocuments = knowledgeSearcher
                .search(
                        evaluationCase.query(),
                        similarityThreshold,
                        topK
                )
                .stream()
                .limit(topK)
                .toList();
        List<String> retrievedSourceIds = retrievedDocuments.stream()
                .map(this::sourceId)
                .toList();

        if (evaluationCase.isNegativeCase()) {
            boolean rejected = retrievedDocuments.isEmpty();
            return new RetrievalEvaluationReport.CaseResult(
                    evaluationCase.id(),
                    evaluationCase.query(),
                    evaluationCase.relevantSourceIds(),
                    retrievedSourceIds,
                    false,
                    0.0,
                    0.0,
                    rejected,
                    rejected
            );
        }

        int relevantCount = 0;
        int firstRelevantRank = 0;
        for (int index = 0; index < retrievedSourceIds.size(); index++) {
            if (evaluationCase.relevantSourceIds()
                    .contains(retrievedSourceIds.get(index))) {
                relevantCount++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = index + 1;
                }
            }
        }

        boolean hit = relevantCount > 0;
        double precisionAtK = (double) relevantCount / topK;
        double reciprocalRank = firstRelevantRank == 0
                ? 0.0
                : 1.0 / firstRelevantRank;

        return new RetrievalEvaluationReport.CaseResult(
                evaluationCase.id(),
                evaluationCase.query(),
                evaluationCase.relevantSourceIds(),
                retrievedSourceIds,
                hit,
                precisionAtK,
                reciprocalRank,
                false,
                hit
        );
    }

    private String sourceId(Document document) {
        return String.valueOf(document.getMetadata().get("sourceId"));
    }

    private double average(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private List<Double> parseThresholds(String configuredThresholds) {
        List<Double> thresholds = List.of(configuredThresholds.split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Double::parseDouble)
                .toList();
        if (thresholds.isEmpty()) {
            throw new IllegalArgumentException("阈值对比列表不能为空");
        }
        if (thresholds.stream().anyMatch(
                threshold -> threshold < 0.0 || threshold > 1.0
        )) {
            throw new IllegalArgumentException("对比阈值必须在 0 到 1 之间");
        }
        return thresholds;
    }
}
