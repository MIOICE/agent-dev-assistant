package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;

@Component
public class BusinessKnowledgeRetriever implements
        KnowledgeSearcher,
        ConfigurableKnowledgeSearcher {

    private final SimpleVectorStore vectorStore;
    private final List<Document> corpusDocuments;
    private final double minSimilarity;
    private final int topK;
    private final double confidenceThreshold;

    public BusinessKnowledgeRetriever(
            SimpleVectorStore vectorStore,
            double minSimilarity,
            int topK
    ) {
        this(vectorStore, List.of(), minSimilarity, topK, minSimilarity);
    }

    @Autowired
    public BusinessKnowledgeRetriever(
            SimpleVectorStore vectorStore,
            BusinessKnowledgeBase knowledgeBase,
            @Value("${app.embedding.similarity-threshold:0.45}")
            double minSimilarity,
            @Value("${app.embedding.top-k:4}")
            int topK,
            @Value("${app.embedding.confidence-threshold:0.65}")
            double confidenceThreshold
    ) {
        this(
                vectorStore,
                knowledgeBase.findAllChunks(),
                minSimilarity,
                topK,
                confidenceThreshold
        );
    }

    BusinessKnowledgeRetriever(
            SimpleVectorStore vectorStore,
            List<Document> corpusDocuments,
            double minSimilarity,
            int topK,
            double confidenceThreshold
    ) {
        this.vectorStore = vectorStore;
        this.corpusDocuments = List.copyOf(corpusDocuments);
        this.minSimilarity = minSimilarity;
        this.topK = topK;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public List<Document> search(String query) {
        return search(query, minSimilarity, topK);
    }

    @Override
    public List<Document> search(
            String query,
            double similarityThreshold,
            int resultLimit
    ) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("相似度阈值必须在 0 到 1 之间");
        }
        if (resultLimit <= 0) {
            throw new IllegalArgumentException("Top-K 必须大于 0");
        }

        int candidateLimit = Math.min(Math.max(resultLimit * 4, resultLimit), 100);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(candidateLimit)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> mergedCandidates = mergeCandidates(
                vectorStore.similaritySearch(searchRequest),
                lexicalCandidates(query)
        );
        List<Document> confidentCandidates = hybridRerank(
                query,
                mergedCandidates
        ).stream()
                .filter(candidate -> score(candidate) >= confidenceThreshold)
                .toList();

        return diversifySources(
                confidentCandidates,
                resultLimit
        );
    }

    private List<Document> lexicalCandidates(String query) {
        if (corpusDocuments.isEmpty()) {
            return List.of();
        }
        return corpusDocuments.stream()
                .filter(document -> lexicalRelevance(query, document) >= 0.20)
                .sorted(Comparator.comparingDouble(
                        (Document document) -> lexicalRelevance(query, document)
                ).reversed())
                .limit(40)
                .toList();
    }

    private List<Document> mergeCandidates(
            List<Document> vectorCandidates,
            List<Document> lexicalCandidates
    ) {
        Map<String, Document> merged = new LinkedHashMap<>();
        vectorCandidates.forEach(document -> merged.put(document.getId(), document));
        lexicalCandidates.forEach(document -> merged.putIfAbsent(document.getId(), document));
        return List.copyOf(merged.values());
    }

    private List<Document> hybridRerank(
            String query,
            List<Document> candidates
    ) {
        return candidates.stream()
                .map(candidate -> applyLexicalBoost(query, candidate))
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .toList();
    }

    private Document applyLexicalBoost(String query, Document candidate) {
        double vectorScore = score(candidate);
        double lexicalScore = lexicalRelevance(query, candidate);
        double rerankScore = vectorScore + 0.25 * lexicalScore;
        if (lexicalScore >= 0.80) {
            rerankScore = Math.max(
                    rerankScore,
                    0.70 + 0.20 * lexicalScore
            );
        }
        rerankScore = Math.min(1.0, rerankScore);
        double lexicalBoost = rerankScore - vectorScore;

        return candidate.mutate()
                .metadata("vectorScore", vectorScore)
                .metadata("lexicalScore", lexicalScore)
                .metadata("lexicalBoost", lexicalBoost)
                .score(rerankScore)
                .build();
    }

    private double lexicalRelevance(String query, Document candidate) {
        String compactQuery = compact(query);
        String title = metadata(candidate, "title");
        String pageTitle = lastTitleSegment(title);
        String module = metadata(candidate, "businessModule");
        String category = metadata(candidate, "businessCategory");
        String heading = metadata(candidate, "headingPath");

        double relevance = 0.0;
        if (containsMeaningful(compactQuery, compact(pageTitle))) {
            relevance = Math.max(relevance, 0.95);
        }
        if (containsMeaningful(compactQuery, compact(category))) {
            relevance = Math.max(relevance, 0.45);
        }
        if (containsMeaningful(compactQuery, compact(module))) {
            relevance = Math.max(relevance, 0.35);
        }
        Object keywordValue = candidate.getMetadata().get("keywords");
        if (keywordValue instanceof List<?> keywords) {
            for (Object keywordValueItem : keywords) {
                String keyword = compact(String.valueOf(keywordValueItem));
                if (containsMeaningful(compactQuery, keyword)) {
                    relevance = Math.max(relevance, keyword.length() >= 4
                            ? 0.90
                            : 0.65);
                }
            }
        }

        String lexicalTarget = compact(
                title + " " + heading + " " + candidate.getText()
        );
        relevance = Math.max(
                relevance,
                0.75 * bigramCoverage(compactQuery, lexicalTarget)
        );
        return Math.min(relevance, 1.0);
    }

    private boolean containsMeaningful(String text, String candidate) {
        return candidate.length() >= 2 && text.contains(candidate);
    }

    private String lastTitleSegment(String title) {
        String[] segments = title.split("[·>]", -1);
        return segments.length == 0
                ? title
                : segments[segments.length - 1].trim();
    }

    private double bigramCoverage(String query, String target) {
        if (query.length() < 2 || target.isBlank()) {
            return 0.0;
        }
        Set<String> queryBigrams = new HashSet<>();
        for (int index = 0; index < query.length() - 1; index++) {
            queryBigrams.add(query.substring(index, index + 2));
        }
        long matched = queryBigrams.stream().filter(target::contains).count();
        return queryBigrams.isEmpty()
                ? 0.0
                : (double) matched / queryBigrams.size();
    }

    private String compact(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private double score(Document document) {
        return document.getScore() == null ? 0.0 : document.getScore();
    }

    private List<Document> diversifySources(
            List<Document> candidates,
            int resultLimit
    ) {
        int maxChunksPerSource = resultLimit == 1 ? 1 : 2;
        Map<String, Integer> countsBySource = new HashMap<>();
        List<Document> selected = new ArrayList<>();

        for (Document candidate : candidates) {
            String sourceId = String.valueOf(
                    candidate.getMetadata().get("sourceId")
            );
            int sourceCount = countsBySource.getOrDefault(sourceId, 0);
            if (sourceCount >= maxChunksPerSource) {
                continue;
            }

            selected.add(candidate);
            countsBySource.put(sourceId, sourceCount + 1);
            if (selected.size() >= resultLimit) {
                break;
            }
        }
        return List.copyOf(selected);
    }
}
