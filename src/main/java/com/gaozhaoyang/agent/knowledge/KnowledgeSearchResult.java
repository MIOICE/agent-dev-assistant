package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;

import java.util.List;

public record KnowledgeSearchResult(
        String id,
        String sourceId,
        String title,
        int chunkIndex,
        List<String> keywords,
        String content,
        double score
) {

    public static KnowledgeSearchResult from(Document document) {
        return new KnowledgeSearchResult(
                document.getId(),
                metadataString(document, "sourceId"),
                metadataString(document, "title"),
                metadataInteger(document, "chunkIndex"),
                metadataStringList(document, "keywords"),
                document.getText(),
                document.getScore() == null ? 0.0 : document.getScore()
        );
    }

    private static String metadataString(Document document, String key) {
        return String.valueOf(document.getMetadata().get(key));
    }

    private static int metadataInteger(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("文档元数据不是数字：" + key);
    }

    private static List<String> metadataStringList(
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
