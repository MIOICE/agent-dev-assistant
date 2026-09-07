package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class KnowledgeControllerTest {

    @Test
    void shouldReturnKnowledgeChunks() throws Exception {
        BusinessKnowledgeBase knowledgeBase = new BusinessKnowledgeBase(
                new BusinessDocumentRepository(),
                new BusinessDocumentChunker()
        );
        SimpleVectorStore vectorStore = SimpleVectorStore
                .builder(new LocalHashEmbeddingModel())
                .build();
        vectorStore.add(knowledgeBase.findAllChunks());
        BusinessKnowledgeRetriever retriever =
                new BusinessKnowledgeRetriever(vectorStore, 0.12, 6);
        RetrievalEvaluator evaluator = new RetrievalEvaluator(
                (query, threshold, topK) -> retriever.search(query),
                List.of(new RetrievalEvaluationCase(
                        "CONTROLLER-CASE",
                        "订单全量导出",
                        List.of("DOC-EXPORT-001")
                )),
                0.12,
                6,
                List.of(0.10, 0.12)
        );
        MockMvc mockMvc = standaloneSetup(
                new KnowledgeController(knowledgeBase, retriever, evaluator)
        ).build();

        mockMvc.perform(get("/api/knowledge/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].sourceId").isNotEmpty())
                .andExpect(jsonPath("$[0].chunkIndex").isNumber())
                .andExpect(jsonPath("$[0].keywords").isArray())
                .andExpect(jsonPath("$[0].content").isNotEmpty());

        mockMvc.perform(get("/api/knowledge/search")
                        .param("query", "订单全量导出"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceId").isNotEmpty())
                .andExpect(jsonPath("$[0].score").isNumber());

        mockMvc.perform(get("/api/knowledge/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(1))
                .andExpect(jsonPath("$.cases").isArray());

        mockMvc.perform(get("/api/knowledge/evaluation/thresholds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].similarityThreshold").value(0.10))
                .andExpect(jsonPath("$[1].similarityThreshold").value(0.12));
    }
}
