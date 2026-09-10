package com.gaozhaoyang.agent.solution;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimEvidenceEvaluationDatasetTest {

    @Test
    void shouldLoadBalancedGoldDatasetFromJson() {
        ClaimEvidenceEvaluationDataset dataset = new ClaimEvidenceEvaluationDataset(
                new ObjectMapper(),
                new ClassPathResource("evaluation/claim-evidence-cases.json")
        );

        assertThat(dataset.findAll()).hasSize(12);
        assertThat(dataset.findAll())
                .filteredOn(item -> item.expectedVerdict() == ClaimEvidenceVerdict.SUPPORTED)
                .hasSize(4);
        assertThat(dataset.findAll())
                .filteredOn(item -> item.expectedVerdict() == ClaimEvidenceVerdict.CONTRADICTED)
                .hasSize(4);
        assertThat(dataset.findAll())
                .filteredOn(item -> item.expectedVerdict() == ClaimEvidenceVerdict.INSUFFICIENT)
                .hasSize(4);
    }

    @Test
    void shouldRejectDuplicateGoldCaseIds() {
        ClaimEvidenceEvaluationCase first = evaluationCase("DUPLICATE");
        ClaimEvidenceEvaluationCase second = evaluationCase("DUPLICATE");

        assertThatThrownBy(() -> new ClaimEvidenceEvaluationDataset(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID 重复");
    }

    private ClaimEvidenceEvaluationCase evaluationCase(String id) {
        return new ClaimEvidenceEvaluationCase(
                id,
                "全量导出必须校验权限",
                "执行全量导出前必须校验角色权限",
                ClaimEvidenceVerdict.SUPPORTED,
                List.of("权限")
        );
    }
}
