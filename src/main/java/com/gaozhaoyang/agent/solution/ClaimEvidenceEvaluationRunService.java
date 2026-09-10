package com.gaozhaoyang.agent.solution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimEvidenceEvaluationRunService {

    private final ClaimEvidenceEvaluator evaluator;
    private final ClaimEvidenceEvaluationDataset dataset;
    private final EvaluationRunRepository repository;
    private final EvaluationGatePolicy gatePolicy;
    private final String aiMode;
    private final String modelId;
    private final String promptVersion;
    private final String datasetVersion;

    @Autowired
    public ClaimEvidenceEvaluationRunService(
            ClaimEvidenceEvaluator evaluator,
            ClaimEvidenceEvaluationDataset dataset,
            EvaluationRunRepository repository,
            @Value("${app.ai.mode}") String aiMode,
            @Value("${app.evaluation.model-id}") String modelId,
            @Value("${app.evaluation.prompt-version}") String promptVersion,
            @Value("${app.evaluation.dataset-version}") String datasetVersion,
            @Value("${app.evaluation.gate.minimum-coverage}") double minimumCoverage,
            @Value("${app.evaluation.gate.minimum-accuracy}") double minimumAccuracy,
            @Value("${app.evaluation.gate.minimum-macro-recall}") double minimumMacroRecall,
            @Value("${app.evaluation.gate.minimum-class-recall}") double minimumClassRecall,
            @Value("${app.evaluation.gate.maximum-regression}") double maximumRegression
    ) {
        this(
                evaluator,
                dataset,
                repository,
                new EvaluationGatePolicy(new EvaluationThresholds(
                        minimumCoverage,
                        minimumAccuracy,
                        minimumMacroRecall,
                        minimumClassRecall,
                        maximumRegression
                )),
                aiMode,
                modelId,
                promptVersion,
                datasetVersion
        );
    }

    ClaimEvidenceEvaluationRunService(
            ClaimEvidenceEvaluator evaluator,
            ClaimEvidenceEvaluationDataset dataset,
            EvaluationRunRepository repository,
            EvaluationGatePolicy gatePolicy,
            String aiMode,
            String modelId,
            String promptVersion,
            String datasetVersion
    ) {
        this.evaluator = evaluator;
        this.dataset = dataset;
        this.repository = repository;
        this.gatePolicy = gatePolicy;
        this.aiMode = aiMode;
        this.modelId = "mock".equalsIgnoreCase(aiMode) ? "mock-not-used" : modelId;
        this.promptVersion = promptVersion;
        this.datasetVersion = datasetVersion;
    }

    public ClaimEvidenceEvaluationRun runAndRecord() {
        ClaimEvidenceEvaluationRun baseline = repository.findLatestPassing().orElse(null);
        ClaimEvidenceEvaluationReport report = evaluator.evaluate();
        EvaluationBaselineComparison comparison = gatePolicy.compare(report, baseline);
        EvaluationGateDecision gate = gatePolicy.decide(report, comparison);
        ClaimEvidenceEvaluationRun run = new ClaimEvidenceEvaluationRun(
                UUID.randomUUID().toString(),
                aiMode,
                modelId,
                promptVersion,
                datasetVersion,
                dataset.fingerprint(),
                report,
                gate,
                comparison,
                Instant.now()
        );
        return repository.save(run);
    }

    public List<ClaimEvidenceEvaluationRun> history(int limit) {
        return repository.findAll(limit);
    }

    public Optional<ClaimEvidenceEvaluationRun> latest() {
        return repository.findAll(1).stream().findFirst();
    }
}
