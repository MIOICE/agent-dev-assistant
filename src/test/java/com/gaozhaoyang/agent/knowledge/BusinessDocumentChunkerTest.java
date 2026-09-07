package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDocumentChunkerTest {

    private final BusinessDocumentChunker chunker =
            new BusinessDocumentChunker();

    @Test
    void shouldSplitLongDocumentIntoMultipleChunks() {
        String longContent = "订单状态修改必须校验状态流转规则。".repeat(80);
        BusinessDocument source = new BusinessDocument(
                "DOC-TEST-001",
                "测试订单规范",
                List.of("订单", "状态"),
                longContent
        );

        List<Document> chunks = chunker.split(List.of(source));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks)
                .extracting(document ->
                        document.getMetadata().get("sourceId")
                )
                .containsOnly("DOC-TEST-001");
    }

    @Test
    void shouldKeepSourceMetadataAndAddChunkIndex() {
        BusinessDocument source = new BusinessDocument(
                "DOC-TEST-002",
                "测试导出规范",
                List.of("导出", "权限"),
                "导出前必须校验用户权限，并记录操作日志。"
        );

        Document chunk = chunker.split(List.of(source)).getFirst();

        assertThat(chunk.getId()).isEqualTo("DOC-TEST-002#chunk-1");
        assertThat(chunk.getMetadata())
                .containsEntry("sourceId", "DOC-TEST-002")
                .containsEntry("title", "测试导出规范")
                .containsEntry("keywords", List.of("导出", "权限"))
                .containsEntry("chunkIndex", 1);
    }
}
