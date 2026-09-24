package com.gaozhaoyang.agent.knowledge;

import java.util.List;
import java.util.Objects;

public record RetrievalEvaluationCase(
        String id,
        String query,
        List<String> relevantSourceIds,
        String category,
        String difficulty
) {

    public RetrievalEvaluationCase {
        Objects.requireNonNull(id, "评测题 ID 不能为空");
        Objects.requireNonNull(query, "评测问题不能为空");
        Objects.requireNonNull(relevantSourceIds, "预期文档列表不能为空");
        if (id.isBlank()) {
            throw new IllegalArgumentException("评测题 ID 不能为空字符串");
        }
        if (query.isBlank()) {
            throw new IllegalArgumentException("评测问题不能为空字符串");
        }
        relevantSourceIds = List.copyOf(relevantSourceIds);
        category = normalize(category, "GENERAL");
        difficulty = normalize(difficulty, "MEDIUM");
        if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            throw new IllegalArgumentException("检索评测难度只允许 EASY、MEDIUM 或 HARD");
        }
    }

    public RetrievalEvaluationCase(
            String id,
            String query,
            List<String> relevantSourceIds
    ) {
        this(id, query, relevantSourceIds, "GENERAL", "MEDIUM");
    }

    public boolean isNegativeCase() {
        return relevantSourceIds.isEmpty();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase();
    }
}
