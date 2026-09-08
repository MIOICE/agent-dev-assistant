package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldAddAvailableMesEvaluationCases(@TempDir Path root)
            throws IOException {
        Path page = root.resolve(
                "summary/订单执行/订单管理/生产订单列表.md"
        );
        Files.createDirectories(page.getParent());
        Files.writeString(
                page,
                "# 生产订单列表\n\n负责订单建立、查询和接口调用。"
        );

        BusinessDocumentRepository repository =
                new BusinessDocumentRepository(
                        true,
                        root.toString(),
                        20,
                        32_768
                );
        RetrievalEvaluationDataset dataset =
                new RetrievalEvaluationDataset(
                        new ObjectMapper(),
                        new ClassPathResource(
                                "evaluation/retrieval-cases.json"
                        ),
                        repository
                );

        assertThat(dataset.findAll()).hasSize(9);
        assertThat(dataset.findAll())
                .extracting(RetrievalEvaluationCase::id)
                .contains("EVAL-MES-001");
        RetrievalEvaluationCase externalCase = dataset.findAll().stream()
                .filter(testCase -> "EVAL-MES-001".equals(testCase.id()))
                .findFirst()
                .orElseThrow();
        assertThat(externalCase.query()).isEqualTo("生产订单列表");
        assertThat(externalCase.relevantSourceIds())
                .containsExactly(
                        "MES:summary/订单执行/订单管理/生产订单列表.md"
                );
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
