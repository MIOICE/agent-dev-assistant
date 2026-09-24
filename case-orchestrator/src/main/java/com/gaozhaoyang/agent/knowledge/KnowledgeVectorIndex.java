package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.vectorstore.SimpleVectorStore;

record KnowledgeVectorIndex(
        SimpleVectorStore vectorStore,
        KnowledgeIndexStatus status
) {
}
