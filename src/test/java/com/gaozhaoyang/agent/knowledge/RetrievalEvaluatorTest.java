package com.gaozhaoyang.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
class RetrievalEvaluatorTest {

    @Test
    void shouldCalculateRetrievalMetrics() {
        ConfigurableKnowledgeSearcher searcher =
                (query, threshold, topK) -> switch (query) {
            case "导出需求" -> List.of(
                        document("DOC-EXPORT-001"),
                        document("DOC-AUTH-001")
                );
            case "无关问题" -> List.of();
            default -> throw new IllegalArgumentException("未设置测试结果");
        };

        RetrievalEvaluator evaluator = new RetrievalEvaluator(
                searcher,
                List.of(
                        new RetrievalEvaluationCase(
                                "POSITIVE",
                                "导出需求",
                                List.of("DOC-EXPORT-001")
                        ),
                        new RetrievalEvaluationCase(
                                "NEGATIVE",
                                "无关问题",
                                List.of()
                        )
                ),
                0.45,
                2,
                List.of(0.40, 0.45)
        );
        RetrievalEvaluationReport report = evaluator.evaluate(List.of(
                new RetrievalEvaluationCase(
                        "POSITIVE",
                        "导出需求",
                        List.of("DOC-EXPORT-001")
                ),
                new RetrievalEvaluationCase(
                        "NEGATIVE",
                        "无关问题",
                        List.of()
                )
        ));

        assertThat(report.similarityThreshold()).isEqualTo(0.45);
        assertThat(report.hitAtK()).isEqualTo(1.0);
        assertThat(report.precisionAtK()).isEqualTo(0.5);
        assertThat(report.meanReciprocalRank()).isEqualTo(1.0);
        assertThat(report.irrelevantRejectionRate()).isEqualTo(1.0);
        assertThat(report.cases()).allMatch(
                RetrievalEvaluationReport.CaseResult::passed
        );
        assertThat(evaluator.compareThresholds())
                .extracting(RetrievalEvaluationReport::similarityThreshold)
                .containsExactly(0.40, 0.45);
    }

    private Document document(String sourceId) {
        return new Document(
                sourceId + "#chunk-1",
                "测试文档",
                Map.of("sourceId", sourceId)
        );
    }
}
