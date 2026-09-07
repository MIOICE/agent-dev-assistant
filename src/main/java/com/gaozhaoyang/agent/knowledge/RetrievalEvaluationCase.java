package com.gaozhaoyang.agent.knowledge;

import java.util.List;
import java.util.Objects;

public record RetrievalEvaluationCase(
        String id,
        String query,
        List<String> relevantSourceIds
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
    }

    public boolean isNegativeCase() {
        return relevantSourceIds.isEmpty();
    }
}
