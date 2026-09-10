package com.gaozhaoyang.agent.solution;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ClaimEvidenceEvaluationRunServiceTest {

    @Test
    void shouldRecordVersionedRunAndCompareNextRunWithPassingBaseline() {
        Fixture fixture = passingFixture();

        ClaimEvidenceEvaluationRun first = fixture.service().runAndRecord();
        ClaimEvidenceEvaluationRun second = fixture.service().runAndRecord();

        assertThat(first.gate().status()).isEqualTo(EvaluationGateStatus.PASSED);
        assertThat(first.baselineComparison().baselineAvailable()).isFalse();
        assertThat(first.datasetFingerprint()).matches("[a-f0-9]{64}");
        assertThat(second.baselineComparison().baselineAvailable()).isTrue();
        assertThat(second.baselineComparison().baselineRunId()).isEqualTo(first.runId());
        assertThat(fixture.repository().findAll(20)).hasSize(2);
    }

    @Test
    void shouldRecordMockRunAsNotEvaluatedWithoutCreatingBaseline() {
        ClaimEvidenceEvaluationDataset dataset = new ClaimEvidenceEvaluationDataset(cases());
        ClaimEvidenceEvaluator evaluator = new ClaimEvidenceEvaluator(
                dataset, new RuleBasedSolutionEvidenceCritic());
        InMemoryEvaluationRunRepository repository = new InMemoryEvaluationRunRepository();
        ClaimEvidenceEvaluationRunService service = service(
                evaluator, dataset, repository, "mock", "mock-not-used");

        ClaimEvidenceEvaluationRun run = service.runAndRecord();

        assertThat(run.gate().status()).isEqualTo(EvaluationGateStatus.NOT_EVALUATED);
        assertThat(run.gate().publishable()).isFalse();
        assertThat(run.modelId()).isEqualTo("mock-not-used");
        assertThat(repository.findLatestPassing()).isEmpty();
    }

    @Test
    void shouldExposeCreateHistoryAndLatestRunEndpoints() throws Exception {
        Fixture fixture = passingFixture();
        MockMvc mockMvc = standaloneSetup(
                new SolutionCritiqueEvaluationController(fixture.service())
        ).build();

        mockMvc.perform(post("/api/solution-critique/evaluation/runs"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gate.status").value("PASSED"))
                .andExpect(jsonPath("$.datasetVersion").value("dataset-v1"))
                .andExpect(jsonPath("$.datasetFingerprint").isString());
        mockMvc.perform(get("/api/solution-critique/evaluation/runs").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gate.status").value("PASSED"));
        mockMvc.perform(get("/api/solution-critique/evaluation/runs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.accuracy").value(1.0));
    }

    private Fixture passingFixture() {
        ClaimEvidenceEvaluationDataset dataset = new ClaimEvidenceEvaluationDataset(cases());
        SolutionCritiqueAssembler assembler = new SolutionCritiqueAssembler();
        SolutionEvidenceCritic critic = (grounding, evidence) -> {
            List<ClaimEvidenceAssessment> predictions = new ArrayList<>();
            for (int index = 0; index < grounding.claims().size(); index++) {
                GroundedSolutionClaim claim = grounding.claims().get(index);
                predictions.add(new ClaimEvidenceAssessment(
                        claim.claimId(),
                        cases().get(index).expectedVerdict(),
                        0.9,
                        "测试判定",
                        claim.evidenceIds()
                ));
            }
            return assembler.assemble("MODEL", grounding, evidence, predictions);
        };
        ClaimEvidenceEvaluator evaluator = new ClaimEvidenceEvaluator(dataset, critic);
        InMemoryEvaluationRunRepository repository = new InMemoryEvaluationRunRepository();
        return new Fixture(
                evaluator,
                repository,
                service(evaluator, dataset, repository, "deepseek", "deepseek-test")
        );
    }

    private ClaimEvidenceEvaluationRunService service(
            ClaimEvidenceEvaluator evaluator,
            ClaimEvidenceEvaluationDataset dataset,
            EvaluationRunRepository repository,
            String aiMode,
            String modelId
    ) {
        return new ClaimEvidenceEvaluationRunService(
                evaluator,
                dataset,
                repository,
                new EvaluationGatePolicy(new EvaluationThresholds(0.9, 0.8, 0.7, 0.6, 0.1)),
                aiMode,
                modelId,
                "prompt-v1",
                "dataset-v1"
        );
    }

    private List<ClaimEvidenceEvaluationCase> cases() {
        return List.of(
                new ClaimEvidenceEvaluationCase(
                        "SUP", "全量导出必须校验权限", "执行全量导出前必须校验权限",
                        ClaimEvidenceVerdict.SUPPORTED, List.of("权限")
                ),
                new ClaimEvidenceEvaluationCase(
                        "CON", "普通用户可以全量导出", "只有管理员可以全量导出",
                        ClaimEvidenceVerdict.CONTRADICTED, List.of("权限")
                ),
                new ClaimEvidenceEvaluationCase(
                        "INS", "导出文件保留七天", "系统必须记录导出审计日志",
                        ClaimEvidenceVerdict.INSUFFICIENT, List.of("生命周期")
                )
        );
    }

    private record Fixture(
            ClaimEvidenceEvaluator evaluator,
            InMemoryEvaluationRunRepository repository,
            ClaimEvidenceEvaluationRunService service
    ) {
    }
}
