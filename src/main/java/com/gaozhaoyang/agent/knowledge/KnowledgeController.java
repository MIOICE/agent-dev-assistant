package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
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

    public KnowledgeController(
            BusinessKnowledgeBase knowledgeBase,
            BusinessKnowledgeRetriever knowledgeRetriever,
            RetrievalEvaluator retrievalEvaluator
    ) {
        this.knowledgeBase = knowledgeBase;
        this.knowledgeRetriever = knowledgeRetriever;
        this.retrievalEvaluator = retrievalEvaluator;
    }

    @GetMapping("/chunks")
    public List<KnowledgeChunkResponse> chunks() {
        return knowledgeBase.findAllChunks().stream()
                .map(this::toResponse)
                .toList();
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
}
