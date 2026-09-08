package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessKnowledgeRetrieverTest {

    private BusinessKnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        BusinessKnowledgeBase knowledgeBase = new BusinessKnowledgeBase(
                new BusinessDocumentRepository(),
                new BusinessDocumentChunker()
        );
        SimpleVectorStore vectorStore = SimpleVectorStore
                .builder(new LocalHashEmbeddingModel())
                .build();
        vectorStore.add(knowledgeBase.findAllChunks());
        retriever = new BusinessKnowledgeRetriever(
                vectorStore,
                0.12,
                6
        );
    }

    @Test
    void shouldReturnRelevantChunksInSimilarityOrder() {
        List<Document> results = retriever.search("订单全量导出");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMetadata().get("sourceId"))
                .isIn("DOC-EXPORT-001", "DOC-ORDER-001");
        assertThat(results.getFirst().getScore()).isPositive();
    }

    @Test
    void shouldReturnEmptyListForBlankQuery() {
        assertThat(retriever.search("   ")).isEmpty();
    }

    @Test
    void shouldMergeExactLexicalMatchOutsideVectorCandidates() {
        Document vectorOnlyDocument = document(
                "VECTOR-ONLY",
                "设备巡检规范",
                List.of("设备", "巡检"),
                "设备每天需要执行点检。"
        );
        Document exactLexicalDocument = document(
                "LEXICAL-EXACT",
                "数据导出规范",
                List.of("导出", "全量导出"),
                "全量导出需要限制任务规模。"
        );
        SimpleVectorStore vectorStore = SimpleVectorStore
                .builder(new LocalHashEmbeddingModel())
                .build();
        vectorStore.add(List.of(vectorOnlyDocument));
        BusinessKnowledgeRetriever hybridRetriever =
                new BusinessKnowledgeRetriever(
                        vectorStore,
                        List.of(vectorOnlyDocument, exactLexicalDocument),
                        0.0,
                        4,
                        0.60
                );

        List<Document> results = hybridRetriever.search("订单全量导出");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getMetadata().get("sourceId"))
                .isEqualTo("LEXICAL-EXACT");
        assertThat(results.getFirst().getMetadata().get("vectorScore"))
                .isEqualTo(0.0);
        assertThat(results.getFirst().getMetadata().get("lexicalScore"))
                .isEqualTo(0.90);
    }

    @Test
    void shouldRejectResultsBelowFinalConfidenceThreshold() {
        Document unrelated = document(
                "UNRELATED",
                "设备巡检规范",
                List.of("设备", "巡检"),
                "设备每天需要执行点检。"
        );
        SimpleVectorStore vectorStore = SimpleVectorStore
                .builder(new LocalHashEmbeddingModel())
                .build();
        vectorStore.add(List.of(unrelated));
        BusinessKnowledgeRetriever strictRetriever =
                new BusinessKnowledgeRetriever(
                        vectorStore,
                        List.of(unrelated),
                        0.0,
                        4,
                        0.99
                );

        assertThat(strictRetriever.search("员工请假怎么审批")).isEmpty();
    }

    private Document document(
            String id,
            String title,
            List<String> keywords,
            String content
    ) {
        return Document.builder()
                .id(id)
                .text(content)
                .metadata("sourceId", id)
                .metadata("title", title)
                .metadata("keywords", keywords)
                .metadata("businessModule", "通用规范")
                .metadata("businessCategory", "")
                .metadata("headingPath", title)
                .build();
    }
}
