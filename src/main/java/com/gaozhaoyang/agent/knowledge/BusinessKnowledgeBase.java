package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocumentRepository;
import com.gaozhaoyang.agent.tool.KnowledgeIngestionStatistics;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessKnowledgeBase {

    private final List<Document> chunks;
    private final KnowledgeCorpusStatus status;

    public BusinessKnowledgeBase(
            BusinessDocumentRepository documentRepository,
            BusinessDocumentChunker documentChunker
    ) {
        this.chunks = documentChunker.split(documentRepository.findAll());
        KnowledgeIngestionStatistics statistics = documentRepository.statistics();
        this.status = new KnowledgeCorpusStatus(
                statistics.externalEnabled(),
                statistics.externalLoaded(),
                statistics.totalDocuments(),
                statistics.bundledDocuments(),
                statistics.externalDocuments(),
                chunks.size(),
                statistics.skippedDocuments(),
                statistics.sanitizedDocuments(),
                statistics.documentsByType()
        );
    }

    public List<Document> findAllChunks() {
        return chunks;
    }

    public KnowledgeCorpusStatus status() {
        return status;
    }
}
