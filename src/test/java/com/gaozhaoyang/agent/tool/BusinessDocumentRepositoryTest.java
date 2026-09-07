package com.gaozhaoyang.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDocumentRepositoryTest {

    private final BusinessDocumentRepository repository =
            new BusinessDocumentRepository();

    @Test
    void shouldLoadAllMarkdownDocumentsFromClasspath() {
        List<BusinessDocument> documents = repository.findAll();

        assertThat(documents)
                .hasSize(3)
                .extracting(BusinessDocument::id)
                .containsExactly(
                        "DOC-ORDER-001",
                        "DOC-EXPORT-001",
                        "DOC-AUTH-001"
                );
    }

    @Test
    void shouldLoadChineseContentAndKeywords() {
        BusinessDocument exportDocument = repository.findAll().stream()
                .filter(document ->
                        "DOC-EXPORT-001".equals(document.id())
                )
                .findFirst()
                .orElseThrow();

        assertThat(exportDocument.title()).isEqualTo("数据导出规范");
        assertThat(exportDocument.keywords())
                .contains("导出", "全量导出", "Excel", "CSV");
        assertThat(exportDocument.content())
                .contains("超过一万条数据时应采用异步导出");
    }
}
