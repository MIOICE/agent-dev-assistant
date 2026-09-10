package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClaimEvidenceEvaluator {

    private static final List<ClaimEvidenceVerdict> GOLD_LABELS = List.of(
            ClaimEvidenceVerdict.SUPPORTED,
            ClaimEvidenceVerdict.CONTRADICTED,
            ClaimEvidenceVerdict.INSUFFICIENT
    );

    private final ClaimEvidenceEvaluationDataset dataset;
    private final SolutionEvidenceCritic critic;

    public ClaimEvidenceEvaluator(
            ClaimEvidenceEvaluationDataset dataset,
            SolutionEvidenceCritic critic
    ) {
        this.dataset = dataset;
        this.critic = critic;
    }

    public ClaimEvidenceEvaluationReport evaluate() {
        List<ClaimEvidenceEvaluationCase> cases = dataset.findAll();
        List<GroundedSolutionClaim> claims = new ArrayList<>();
        List<KnowledgeSearchResult> evidence = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            ClaimEvidenceEvaluationCase evaluationCase = cases.get(index);
            String claimId = claimId(evaluationCase);
            String evidenceId = evidenceId(evaluationCase);
            claims.add(new GroundedSolutionClaim(
                    claimId, "EVALUATION", index, evaluationCase.claim(), List.of(evidenceId),
                    ClaimGroundingStatus.EVIDENCE_LINKED, "人工金标评测证据"
            ));
            evidence.add(toEvidence(evaluationCase, evidenceId));
        }
        SolutionGroundingReport grounding = new SolutionGroundingReport(
                claims, claims.size(), claims.size(), 0, 0, 1.0, true, List.of()
        );
        SolutionCritiqueReport critique = critic.critique(grounding, evidence);
        Map<String, ClaimEvidenceAssessment> assessmentByClaimId = new LinkedHashMap<>();
        for (ClaimEvidenceAssessment assessment : critique.assessments()) {
            assessmentByClaimId.putIfAbsent(assessment.claimId(), assessment);
        }

        List<ClaimEvidenceEvaluationReport.CaseResult> results = new ArrayList<>();
        for (ClaimEvidenceEvaluationCase evaluationCase : cases) {
            ClaimEvidenceAssessment assessment = assessmentByClaimId.get(claimId(evaluationCase));
            ClaimEvidenceVerdict actual = assessment == null
                    ? ClaimEvidenceVerdict.NOT_EVALUATED
                    : assessment.verdict();
            boolean evaluated = GOLD_LABELS.contains(actual);
            results.add(new ClaimEvidenceEvaluationReport.CaseResult(
                    evaluationCase.id(),
                    evaluationCase.claim(),
                    evaluationCase.tags(),
                    evaluationCase.expectedVerdict(),
                    actual,
                    assessment == null ? 0.0 : assessment.confidence(),
                    evaluated,
                    evaluated && actual == evaluationCase.expectedVerdict(),
                    assessment == null ? "审查器未返回该样例。" : assessment.rationale()
            ));
        }
        return buildReport(critique.mode(), results);
    }

    ClaimEvidenceEvaluationReport buildReport(
            String mode,
            List<ClaimEvidenceEvaluationReport.CaseResult> results
    ) {
        int total = results.size();
        int evaluated = (int) results.stream()
                .filter(ClaimEvidenceEvaluationReport.CaseResult::evaluated)
                .count();
        int correct = (int) results.stream()
                .filter(ClaimEvidenceEvaluationReport.CaseResult::correct)
                .count();
        double supportedRecall = recall(results, ClaimEvidenceVerdict.SUPPORTED);
        double contradictedRecall = recall(results, ClaimEvidenceVerdict.CONTRADICTED);
        double insufficientRecall = recall(results, ClaimEvidenceVerdict.INSUFFICIENT);
        double macroRecall = round((supportedRecall + contradictedRecall + insufficientRecall) / 3.0);
        List<String> warnings = new ArrayList<>();
        warnings.add("评测集由 " + total + " 条人工标注的合成业务样例组成，不代表真实生产流量准确率。");
        if (evaluated < total) {
            warnings.add("有 " + (total - evaluated) + " 条样例未执行语义判定，准确率仅以已评估样例为分母。");
        }
        warnings.add("上线前还需扩充真实脱敏样例，并由业务与研发共同复核金标。");
        return new ClaimEvidenceEvaluationReport(
                mode,
                total,
                evaluated,
                correct,
                ratio(evaluated, total),
                ratio(correct, evaluated),
                supportedRecall,
                contradictedRecall,
                insufficientRecall,
                macroRecall,
                confusionMatrix(results),
                results,
                warnings,
                Instant.now()
        );
    }

    private double recall(
            List<ClaimEvidenceEvaluationReport.CaseResult> results,
            ClaimEvidenceVerdict expected
    ) {
        long expectedCount = results.stream()
                .filter(result -> result.expectedVerdict() == expected)
                .count();
        long correctCount = results.stream()
                .filter(result -> result.expectedVerdict() == expected && result.correct())
                .count();
        return ratio(correctCount, expectedCount);
    }

    private Map<String, Map<String, Integer>> confusionMatrix(
            List<ClaimEvidenceEvaluationReport.CaseResult> results
    ) {
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (ClaimEvidenceVerdict expected : GOLD_LABELS) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (ClaimEvidenceVerdict actual : ClaimEvidenceVerdict.values()) {
                row.put(actual.name(), 0);
            }
            matrix.put(expected.name(), row);
        }
        for (ClaimEvidenceEvaluationReport.CaseResult result : results) {
            Map<String, Integer> row = matrix.get(result.expectedVerdict().name());
            row.compute(result.actualVerdict().name(), (key, value) -> value == null ? 1 : value + 1);
        }
        Map<String, Map<String, Integer>> immutable = new LinkedHashMap<>();
        matrix.forEach((key, value) -> immutable.put(key, Map.copyOf(value)));
        return Map.copyOf(immutable);
    }

    private KnowledgeSearchResult toEvidence(
            ClaimEvidenceEvaluationCase evaluationCase,
            String evidenceId
    ) {
        return new KnowledgeSearchResult(
                evidenceId,
                "GOLD-" + evaluationCase.id(),
                "Claim-Evidence 人工金标样例",
                1,
                evaluationCase.tags(),
                "synthetic-evaluation",
                "classpath:evaluation/claim-evidence-cases.json",
                "评测",
                "语义审查",
                "GOLD_CASE",
                evaluationCase.id(),
                true,
                evaluationCase.evidence(),
                1.0,
                1.0,
                0.0
        );
    }

    private String claimId(ClaimEvidenceEvaluationCase evaluationCase) {
        return "EVAL-CLAIM-" + evaluationCase.id();
    }

    private String evidenceId(ClaimEvidenceEvaluationCase evaluationCase) {
        return "EVAL-EVIDENCE-" + evaluationCase.id();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : round((double) numerator / denominator);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
