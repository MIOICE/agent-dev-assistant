package com.gaozhaoyang.agent.knowledge;

import java.util.List;

public record KnowledgeChunkResponse(
        String id,
        String sourceId,
        String title,
        int chunkIndex,
        List<String> keywords,
        String content
) {
}
