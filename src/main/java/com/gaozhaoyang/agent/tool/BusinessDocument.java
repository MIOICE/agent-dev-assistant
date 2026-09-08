package com.gaozhaoyang.agent.tool;

import java.util.List;

/**
 * 从知识库文件中加载的一份业务文档。
 */
public record BusinessDocument(
        String id,
        String title,
        List<String> keywords,
        String content,
        String sourceType,
        String sourcePath,
        String businessModule,
        String businessCategory,
        String documentType,
        boolean sanitized
) {

    public BusinessDocument {
        keywords = List.copyOf(keywords);
    }

    public BusinessDocument(
            String id,
            String title,
            List<String> keywords,
            String content
    ) {
        this(
                id,
                title,
                keywords,
                content,
                "bundled",
                "",
                "通用规范",
                "",
                "BUSINESS_POLICY",
                false
        );
    }
}
