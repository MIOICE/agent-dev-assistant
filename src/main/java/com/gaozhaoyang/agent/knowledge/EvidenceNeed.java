package com.gaozhaoyang.agent.knowledge;

import java.util.Objects;

public record EvidenceNeed(
        String id,
        String query,
        String purpose,
        boolean required
) {

    public EvidenceNeed {
        id = normalize(id, "证据需求 ID");
        query = normalize(query, "检索问题");
        purpose = normalize(purpose, "检索目的");
        if (id.length() > 40) {
            throw new IllegalArgumentException("证据需求 ID 最多40字");
        }
        if (query.length() > 300) {
            throw new IllegalArgumentException("检索问题最多300字");
        }
        if (purpose.length() > 200) {
            throw new IllegalArgumentException("检索目的最多200字");
        }
    }

    private static String normalize(String value, String label) {
        Objects.requireNonNull(value, label + "不能为空");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空字符串");
        }
        return normalized;
    }
}
