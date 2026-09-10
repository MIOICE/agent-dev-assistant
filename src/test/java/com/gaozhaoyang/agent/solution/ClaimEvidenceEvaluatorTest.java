package com.gaozhaoyang.agent.solution;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ClaimEvidenceEvaluatorTest {

    @Test
    void shouldCalculateCoverageAccuracyRecallAndConfusionMatrix() {
        ClaimEvidenceEvaluator evaluator = evaluatorWithPredictions();

        ClaimEvidenceEvaluationReport report = evaluator.evaluate();

        assertThat(report.mode()).isEqualTo("MODEL");
        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.evaluatedCases()).isEqualTo(3);
        assertThat(report.correctCases()).isEqualTo(2);
        assertThat(report.coverage()).isEqualTo(1.0);
        assertThat(report.accuracy()).isEqualTo(0.6667);
        assertThat(report.supportedRecall()).isEqualTo(1.0);
        assertThat(report.contradictedRecall()).isZero();
        assertThat(report.insufficientRecall()).isEqualTo(1.0);
        assertThat(report.macroRecall()).isEqualTo(0.6667);
        assertThat(report.confusionMatrix().get("CONTRADICTED"))
                .containsEntry("INSUFFICIENT", 1);
    }

    @Test
    void shouldReportZeroCoverageInsteadOfFakeAccuracyInMockMode() {
        ClaimEvidenceEvaluator evaluator = new ClaimEvidenceEvaluator(
                new ClaimEvidenceEvaluationDataset(sampleCases()),
                new RuleBasedSolutionEvidenceCritic()
        );

        ClaimEvidenceEvaluationReport report = evaluator.evaluate();

        assertThat(report.mode()).isEqualTo("RULE_BASED_NOT_EVALUATED");
        assertThat(report.evaluatedCases()).isZero();
        assertThat(report.coverage()).isZero();
        assertThat(report.accuracy()).isZero();
        assertThat(report.cases())
                .extracting(ClaimEvidenceEvaluationReport.CaseResult::actualVerdict)
                .containsOnly(ClaimEvidenceVerdict.NOT_EVALUATED);
        assertThat(report.warnings()).anyMatch(value -> value.contains("未执行语义判定"));
    }

    @Test
    void shouldExposeEvaluationEndpoint() throws Exception {
        MockMvc mockMvc = standaloneSetup(
                new SolutionCritiqueEvaluationController(evaluatorWithPredictions())
        ).build();

        mockMvc.perform(get("/api/solution-critique/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(3))
                .andExpect(jsonPath("$.evaluatedCases").value(3))
                .andExpect(jsonPath("$.accuracy").value(0.6667))
                .andExpect(jsonPath("$.confusionMatrix.CONTRADICTED.INSUFFICIENT").value(1))
                .andExpect(jsonPath("$.cases").isArray());
    }

    private ClaimEvidenceEvaluator evaluatorWithPredictions() {
        SolutionCritiqueAssembler assembler = new SolutionCritiqueAssembler();
        SolutionEvidenceCritic critic = (grounding, evidence) -> {
            List<ClaimEvidenceAssessment> predictions = new ArrayList<>();
            for (int index = 0; index < grounding.claims().size(); index++) {
                GroundedSolutionClaim claim = grounding.claims().get(index);
                ClaimEvidenceVerdict verdict = index == 0
                        ? ClaimEvidenceVerdict.SUPPORTED
                        : ClaimEvidenceVerdict.INSUFFICIENT;
                predictions.add(new ClaimEvidenceAssessment(
                        claim.claimId(), verdict, 0.8, "测试判定", claim.evidenceIds()
                ));
            }
            return assembler.assemble("MODEL", grounding, evidence, predictions);
        };
        return new ClaimEvidenceEvaluator(
                new ClaimEvidenceEvaluationDataset(sampleCases()),
                critic
        );
    }

    private List<ClaimEvidenceEvaluationCase> sampleCases() {
        return List.of(
                new ClaimEvidenceEvaluationCase(
                        "SUP", "全量导出需要权限", "全量导出必须检查权限",
                        ClaimEvidenceVerdict.SUPPORTED, List.of("权限")
                ),
                new ClaimEvidenceEvaluationCase(
                        "CON", "普通用户可以全量导出", "只有管理员可以全量导出",
                        ClaimEvidenceVerdict.CONTRADICTED, List.of("权限")
                ),
                new ClaimEvidenceEvaluationCase(
                        "INS", "文件保留七天", "系统需要记录导出审计日志",
                        ClaimEvidenceVerdict.INSUFFICIENT, List.of("生命周期")
                )
        );
    }
}
