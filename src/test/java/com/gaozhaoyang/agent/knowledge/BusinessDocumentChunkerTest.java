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

    @Test
    void shouldSplitMarkdownByHeadingAndKeepBusinessSourceMetadata() {
        BusinessDocument source = new BusinessDocument(
                "MES:summary/订单执行/订单管理/生产订单列表.md",
                "生产订单列表",
                List.of("订单执行", "订单管理"),
                """
                        # 生产订单列表

                        ## 页面职责
                        负责订单查询和建立。

                        ## 核心表
                        使用 wafer_store 保存订单。
                        """,
                "external-mes",
                "summary/订单执行/订单管理/生产订单列表.md",
                "订单执行",
                "订单管理",
                "BUSINESS_PAGE",
                false
        );

        List<Document> chunks = chunker.split(List.of(source));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks)
                .extracting(document ->
                        document.getMetadata().get("sourcePath")
                )
                .containsOnly("summary/订单执行/订单管理/生产订单列表.md");
        assertThat(chunks)
                .extracting(document ->
                        document.getMetadata().get("headingPath")
                )
                .anyMatch(path -> String.valueOf(path).contains("页面职责"))
                .anyMatch(path -> String.valueOf(path).contains("核心表"));
        assertThat(chunks.getFirst().getText())
                .contains("订单执行", "生产订单列表");
    }
}
