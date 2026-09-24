package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionCritiqueAssemblerTest {

    private final SolutionCritiqueAssembler assembler = new SolutionCritiqueAssembler();

    @Test
    void shouldValidateClaimAndEvidenceIdsBeforeAggregatingVerdicts() {
        SolutionGroundingReport grounding = grounding();
        List<ClaimEvidenceAssessment> candidates = List.of(
                new ClaimEvidenceAssessment(
                        "BACKEND-1", ClaimEvidenceVerdict.SUPPORTED, 0.91,
                        "证据明确要求使用异步任务。", List.of("e1")
                ),
                new ClaimEvidenceAssessment(
                        "SECURITY-1", ClaimEvidenceVerdict.CONTRADICTED, 0.83,
                        "证据要求管理员权限，而结论允许普通用户。", List.of("e2", "e999")
                ),
                new ClaimEvidenceAssessment(
                        "API-1", ClaimEvidenceVerdict.SUPPORTED, 0.95,
                        "使用了不属于该结论的引用。", List.of("e999")
                ),
                new ClaimEvidenceAssessment(
                        "UNKNOWN-1", ClaimEvidenceVerdict.SUPPORTED, 1.0,
                        "不能进入最终报告。", List.of("e1")
                )
        );

        SolutionCritiqueReport report = assembler.assemble(
                "MODEL", grounding,
                List.of(evidence("e1"), evidence("e2"), evidence("e3")), candidates
        );

        assertThat(report.assessments()).hasSize(5);
        assertThat(report.supportedClaims()).isEqualTo(1);
        assertThat(report.contradictedClaims()).isEqualTo(1);
        assertThat(report.insufficientClaims()).isEqualTo(2);
        assertThat(report.notEvaluatedClaims()).isZero();
        assertThat(report.assumptionClaims()).isEqualTo(1);
        assertThat(report.totalFactualClaims()).isEqualTo(4);
        assertThat(report.supportRate()).isEqualTo(0.25);
        assertThat(report.safeForApproval()).isFalse();
        assertThat(report.assessments())
                .filteredOn(item -> item.claimId().equals("SECURITY-1"))
                .flatExtracting(ClaimEvidenceAssessment::evidenceIds)
                .containsExactly("e2");
        assertThat(report.assessments())
                .filteredOn(item -> item.claimId().equals("API-1"))
                .extracting(ClaimEvidenceAssessment::verdict)
                .containsExactly(ClaimEvidenceVerdict.INSUFFICIENT);
        assertThat(report.assessments())
                .extracting(ClaimEvidenceAssessment::claimId)
                .doesNotContain("UNKNOWN-1");
    }

    @Test
    void shouldExposeMissingModelAssessmentsInsteadOfAssumingSupport() {
        SolutionGroundingReport grounding = new SolutionGroundingReport(
                List.of(new GroundedSolutionClaim(
                        "API-1", "API", 0, "新增导出接口", List.of("e1"),
                        ClaimGroundingStatus.EVIDENCE_LINKED, "已关联证据"
                )),
                1, 1, 0, 0, 1.0, true, List.of()
        );

        SolutionCritiqueReport report = assembler.assemble(
                "MODEL", grounding, List.of(evidence("e1")), List.of()
        );

        assertThat(report.notEvaluatedClaims()).isEqualTo(1);
        assertThat(report.supportedClaims()).isZero();
        assertThat(report.safeForApproval()).isFalse();
        assertThat(report.assessments().getFirst().verdict())
                .isEqualTo(ClaimEvidenceVerdict.NOT_EVALUATED);
    }

    private SolutionGroundingReport grounding() {
        return new SolutionGroundingReport(
                List.of(
                        new GroundedSolutionClaim("BACKEND-1", "BACKEND", 0,
                                "大数据导出使用异步任务", List.of("e1"),
                                ClaimGroundingStatus.EVIDENCE_LINKED, "已关联证据"),
                        new GroundedSolutionClaim("SECURITY-1", "SECURITY", 0,
                                "普通用户可以全量导出", List.of("e2"),
                                ClaimGroundingStatus.EVIDENCE_LINKED, "已关联证据"),
                        new GroundedSolutionClaim("DATABASE-1", "DATABASE", 0,
                                "新增未知字段", List.of(),
                                ClaimGroundingStatus.UNSUPPORTED, "没有证据"),
                        new GroundedSolutionClaim("API-1", "API", 0,
                                "新增导出接口", List.of("e3"),
                                ClaimGroundingStatus.EVIDENCE_LINKED, "已关联证据"),
                        new GroundedSolutionClaim("ASSUMPTION-1", "ASSUMPTION", 0,
                                "导出格式暂定CSV", List.of(),
                                ClaimGroundingStatus.ASSUMPTION, "明确假设")
                ),
                4, 3, 1, 1, 0.75, false, List.of()
        );
    }

    private KnowledgeSearchResult evidence(String id) {
        return new KnowledgeSearchResult(
                id, "DOC-1", "导出规范", 1, List.of("导出"),
                "TEST", "docs/export.md", "订单", "导出", "Markdown",
                "导出规范", false, "证据正文", 0.9, 0.8, 0.1
        );
    }
}
