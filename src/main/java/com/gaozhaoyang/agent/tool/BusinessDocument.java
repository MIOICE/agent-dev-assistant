package com.gaozhaoyang.agent.tool;

import java.util.List;

/**
 * 从知识库文件中加载的一份业务文档。
 */
public record BusinessDocument(
        String id,
        String title,
        List<String> keywords,
        String content
) {

    public BusinessDocument {
        keywords = List.copyOf(keywords);
    }
}
