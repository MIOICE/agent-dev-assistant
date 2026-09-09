package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final BusinessKnowledgeBase knowledgeBase;
    private final BusinessKnowledgeRetriever knowledgeRetriever;
    private final RetrievalEvaluator retrievalEvaluator;
    private final KnowledgeIndexStatus indexStatus;

    @Autowired
    public KnowledgeController(
            BusinessKnowledgeBase knowledgeBase,
            BusinessKnowledgeRetriever knowledgeRetriever,
            RetrievalEvaluator retrievalEvaluator,
            KnowledgeIndexStatus indexStatus
    ) {
        this.knowledgeBase = knowledgeBase;
        this.knowledgeRetriever = knowledgeRetriever;
        this.retrievalEvaluator = retrievalEvaluator;
        this.indexStatus = indexStatus;
    }

    KnowledgeController(
            BusinessKnowledgeBase knowledgeBase,
            BusinessKnowledgeRetriever knowledgeRetriever,
            RetrievalEvaluator retrievalEvaluator
    ) {
        this(
                knowledgeBase,
                knowledgeRetriever,
                retrievalEvaluator,
                KnowledgeIndexStatus.disabled(
                        knowledgeBase.findAllChunks().size()
                )
        );
    }

    @GetMapping("/chunks")
    public List<KnowledgeChunkResponse> chunks(
            @RequestParam(defaultValue = "200") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return knowledgeBase.findAllChunks().stream()
                .limit(safeLimit)
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/status")
    public KnowledgeCorpusStatus status() {
        return knowledgeBase.status();
    }

    @GetMapping("/index/status")
    public KnowledgeIndexStatus indexStatus() {
        return indexStatus;
    }

    @GetMapping("/search")
    public List<KnowledgeSearchResult> search(
            @RequestParam String query
    ) {
        return knowledgeRetriever.search(query).stream()
                .map(KnowledgeSearchResult::from)
                .toList();
    }

    @GetMapping("/evaluation")
    public RetrievalEvaluationReport evaluation() {
        return retrievalEvaluator.evaluate();
    }

    @GetMapping("/evaluation/thresholds")
    public List<RetrievalEvaluationReport> thresholdEvaluation() {
        return retrievalEvaluator.compareThresholds();
    }

    private KnowledgeChunkResponse toResponse(Document document) {
        return new KnowledgeChunkResponse(
                document.getId(),
                metadataString(document, "sourceId"),
                metadataString(document, "title"),
                metadataInteger(document, "chunkIndex"),
                metadataStringList(document, "keywords"),
                metadataString(document, "sourceType"),
                metadataString(document, "sourcePath"),
                metadataString(document, "businessModule"),
                metadataString(document, "businessCategory"),
                metadataString(document, "documentType"),
                metadataString(document, "headingPath"),
                metadataBoolean(document, "sanitized"),
                document.getText()
        );
    }

    private String metadataString(Document document, String key) {
        return String.valueOf(document.getMetadata().get(key));
    }

    private int metadataInteger(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("文档元数据不是数字：" + key);
    }

    private List<String> metadataStringList(
            Document document,
            String key
    ) {
        Object value = document.getMetadata().get(key);
        if (!(value instanceof List<?> values)) {
            throw new IllegalStateException("文档元数据不是列表：" + key);
        }
        return values.stream().map(String::valueOf).toList();
    }

    private boolean metadataBoolean(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value instanceof Boolean booleanValue && booleanValue;
    }
}
