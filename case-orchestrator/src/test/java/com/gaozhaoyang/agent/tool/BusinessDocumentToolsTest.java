package com.gaozhaoyang.agent.tool;

import com.gaozhaoyang.agent.knowledge.BusinessDocumentChunker;
import com.gaozhaoyang.agent.knowledge.BusinessKnowledgeBase;
import com.gaozhaoyang.agent.knowledge.BusinessKnowledgeRetriever;
import com.gaozhaoyang.agent.knowledge.LocalHashEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDocumentToolsTest {

    private final BusinessDocumentTools tools = createTools();

    @Test
    void shouldFindOrderAndExportDocuments() {
        String result = tools.searchBusinessDocument(
                "订单列表增加全量导出功能"
        );

        assertThat(result)
                .contains("DOC-ORDER-001")
                .contains("DOC-EXPORT-001")
                .contains("DOC-AUTH-001");
    }

    @Test
    void shouldFindOrderRulesForBatchModification() {
        String result = tools.searchBusinessDocument(
                "批量修改订单状态"
        );

        assertThat(result)
                .contains("批量修改订单状态前，系统必须逐条校验")
                .doesNotContain("数据导出规范");
    }

    @Test
    void shouldReturnMessageWhenNoDocumentMatches() {
        String result = tools.searchBusinessDocument("修改页面颜色");

        assertThat(result).isEqualTo("未找到与当前需求相关的业务规范");
    }

    @Test
    void shouldRejectBlankQuery() {
        String result = tools.searchBusinessDocument("   ");

        assertThat(result).isEqualTo("查询关键词不能为空");
    }

    private BusinessDocumentTools createTools() {
        BusinessKnowledgeBase knowledgeBase = new BusinessKnowledgeBase(
                new BusinessDocumentRepository(),
                new BusinessDocumentChunker()
        );
        SimpleVectorStore vectorStore = SimpleVectorStore
                .builder(new LocalHashEmbeddingModel())
                .build();
        vectorStore.add(knowledgeBase.findAllChunks());

        return new BusinessDocumentTools(
                new BusinessKnowledgeRetriever(vectorStore, 0.12, 6)
        );
    }
}
