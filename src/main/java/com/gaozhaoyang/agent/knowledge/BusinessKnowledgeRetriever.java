package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessKnowledgeRetriever implements
        KnowledgeSearcher,
        ConfigurableKnowledgeSearcher {

    private final SimpleVectorStore vectorStore;
    private final double minSimilarity;
    private final int topK;

    public BusinessKnowledgeRetriever(
            SimpleVectorStore vectorStore,
            @Value("${app.embedding.similarity-threshold:0.45}")
            double minSimilarity,
            @Value("${app.embedding.top-k:4}")
            int topK
    ) {
        this.vectorStore = vectorStore;
        this.minSimilarity = minSimilarity;
        this.topK = topK;
    }

    @Override
    public List<Document> search(String query) {
        return search(query, minSimilarity, topK);
    }

    @Override
    public List<Document> search(
            String query,
            double similarityThreshold,
            int resultLimit
    ) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("相似度阈值必须在 0 到 1 之间");
        }
        if (resultLimit <= 0) {
            throw new IllegalArgumentException("Top-K 必须大于 0");
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(resultLimit)
                .similarityThreshold(similarityThreshold)
                .build();

        return vectorStore.similaritySearch(searchRequest);
    }
}
