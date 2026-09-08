package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;

import java.util.List;

public record KnowledgeSearchResult(
        String id,
        String sourceId,
        String title,
        int chunkIndex,
        List<String> keywords,
        String sourceType,
        String sourcePath,
        String businessModule,
        String businessCategory,
        String documentType,
        String headingPath,
        boolean sanitized,
        String content,
        double score,
        double vectorScore,
        double lexicalBoost
) {

    public static KnowledgeSearchResult from(Document document) {
        return new KnowledgeSearchResult(
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
                document.getText(),
                document.getScore() == null ? 0.0 : document.getScore(),
                metadataDouble(document, "vectorScore"),
                metadataDouble(document, "lexicalBoost")
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

    private static boolean metadataBoolean(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private static double metadataDouble(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
