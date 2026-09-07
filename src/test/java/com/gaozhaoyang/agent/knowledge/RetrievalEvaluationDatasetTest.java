package com.gaozhaoyang.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
class RetrievalEvaluationDatasetTest {

    @Test
    void shouldLoadEvaluationCasesFromJson() {
        RetrievalEvaluationDataset dataset =
                new RetrievalEvaluationDataset(
                        new ObjectMapper(),
                        new ClassPathResource(
                                "evaluation/retrieval-cases.json"
                        )
                );

        assertThat(dataset.findAll()).hasSize(8);
        assertThat(dataset.findAll())
                .filteredOn(RetrievalEvaluationCase::isNegativeCase)
                .hasSize(2);
    }
    @Test
void shouldRejectDuplicateCaseIds() {
    RetrievalEvaluationCase first = new RetrievalEvaluationCase(
            "EVAL-001",
            "第一个问题",
            List.of("DOC-EXPORT-001")
    );

    RetrievalEvaluationCase second = new RetrievalEvaluationCase(
            "EVAL-001",
            "第二个问题",
            List.of("DOC-ORDER-001")
    );

    assertThatThrownBy(() ->
            new RetrievalEvaluationDataset(List.of(first, second))
    )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ID 重复");
}
}
