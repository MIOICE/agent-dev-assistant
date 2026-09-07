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
}
