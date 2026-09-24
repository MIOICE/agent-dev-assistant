package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;

import java.util.List;

@FunctionalInterface
public interface ConfigurableKnowledgeSearcher {

    List<Document> search(
            String query,
            double similarityThreshold,
            int topK
    );
}
