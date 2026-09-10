package com.gaozhaoyang.agent.solution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedSolutionEvidenceCriticTest {

    @Test
    void shouldHonestlyLeaveLinkedClaimsUnevaluatedInMockMode() {
        SolutionGroundingReport grounding = new SolutionGroundingReport(
                List.of(
                        new GroundedSolutionClaim(
                                "BACKEND-1", "BACKEND", 0, "采用异步任务", List.of("e1"),
                                ClaimGroundingStatus.EVIDENCE_LINKED, "已关联证据"
                        ),
                        new GroundedSolutionClaim(
                                "ASSUMPTION-1", "ASSUMPTION", 0, "导出格式暂定CSV", List.of(),
                                ClaimGroundingStatus.ASSUMPTION, "明确假设"
                        )
                ),
                1, 1, 1, 0, 1.0, true, List.of()
        );

        SolutionCritiqueReport report = new RuleBasedSolutionEvidenceCritic()
                .critique(grounding, List.of());

        assertThat(report.mode()).isEqualTo("RULE_BASED_NOT_EVALUATED");
        assertThat(report.notEvaluatedClaims()).isEqualTo(1);
        assertThat(report.assumptionClaims()).isEqualTo(1);
        assertThat(report.supportedClaims()).isZero();
        assertThat(report.safeForApproval()).isFalse();
        assertThat(report.warnings()).anyMatch(value -> value.contains("未执行模型语义判定"));
    }
}
