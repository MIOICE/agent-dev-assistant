package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfiguration {

    @Bean
    public SimpleVectorStore businessVectorStore(
            @Qualifier("businessEmbeddingModel")
            EmbeddingModel embeddingModel,
            BusinessKnowledgeBase knowledgeBase
    ) {
        SimpleVectorStore vectorStore = SimpleVectorStore
                .builder(embeddingModel)
                .build();

        vectorStore.add(knowledgeBase.findAllChunks());
        return vectorStore;
    }
}
