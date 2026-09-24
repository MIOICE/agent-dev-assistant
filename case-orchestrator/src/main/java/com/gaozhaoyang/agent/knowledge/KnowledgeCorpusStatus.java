package com.gaozhaoyang.agent.knowledge;

import java.util.Map;

public record KnowledgeCorpusStatus(
        boolean externalEnabled,
        boolean externalLoaded,
        int sourceDocuments,
        int bundledDocuments,
        int externalDocuments,
        int chunks,
        int skippedDocuments,
        int sanitizedDocuments,
        Map<String, Integer> documentsByType
) {

    public KnowledgeCorpusStatus {
        documentsByType = Map.copyOf(documentsByType);
    }
}
