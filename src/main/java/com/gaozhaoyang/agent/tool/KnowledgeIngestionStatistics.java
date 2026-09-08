package com.gaozhaoyang.agent.tool;

import java.util.Map;

public record KnowledgeIngestionStatistics(
        boolean externalEnabled,
        boolean externalLoaded,
        int bundledDocuments,
        int externalDocuments,
        int skippedDocuments,
        int sanitizedDocuments,
        Map<String, Integer> documentsByType
) {

    public KnowledgeIngestionStatistics {
        documentsByType = Map.copyOf(documentsByType);
    }

    public int totalDocuments() {
        return bundledDocuments + externalDocuments;
    }
}
