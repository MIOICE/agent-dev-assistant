package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class VectorStoreConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            VectorStoreConfiguration.class
    );

    @Bean
    public KnowledgeVectorIndex businessKnowledgeVectorIndex(
            @Qualifier("businessEmbeddingModel")
            EmbeddingModel embeddingModel,
            BusinessKnowledgeBase knowledgeBase,
            KnowledgeVectorIndexManager indexManager,
            @Value("${app.knowledge.index.enabled:true}")
            boolean cacheEnabled,
            @Value("${app.knowledge.index.root:F:/agent-workspaces/knowledge-index}")
            String indexRoot,
            @Value("${app.knowledge.index.model-id:bge-small-zh-v1.5-v1}")
            String modelId
    ) {
        KnowledgeVectorIndex index = indexManager.initialize(
                embeddingModel,
                knowledgeBase.findAllChunks(),
                cacheEnabled,
                Path.of(indexRoot),
                modelId
        );
        KnowledgeIndexStatus status = index.status();
        LOGGER.info(
                "Knowledge index ready: mode={}, chunks={}, reused={}, "
                        + "added={}, updated={}, removed={}",
                status.mode(),
                status.currentChunks(),
                status.reusedChunks(),
                status.addedChunks(),
                status.updatedChunks(),
                status.removedChunks()
        );
        return index;
    }

    @Bean
    public SimpleVectorStore businessVectorStore(
            KnowledgeVectorIndex vectorIndex
    ) {
        return vectorIndex.vectorStore();
    }

    @Bean
    public KnowledgeIndexStatus knowledgeIndexStatus(
            KnowledgeVectorIndex vectorIndex
    ) {
        return vectorIndex.status();
    }
}
