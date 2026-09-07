package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessKnowledgeBase {

    private final List<Document> chunks;

    public BusinessKnowledgeBase(
            BusinessDocumentRepository documentRepository,
            BusinessDocumentChunker documentChunker
    ) {
        this.chunks = documentChunker.split(documentRepository.findAll());
    }

    public List<Document> findAllChunks() {
        return chunks;
    }
}
