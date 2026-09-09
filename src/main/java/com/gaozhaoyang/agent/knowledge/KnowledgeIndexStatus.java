package com.gaozhaoyang.agent.knowledge;

public record KnowledgeIndexStatus(
        String mode,
        boolean cacheEnabled,
        boolean snapshotLoaded,
        int currentChunks,
        int reusedChunks,
        int addedChunks,
        int updatedChunks,
        int removedChunks,
        String modelId,
        String corpusFingerprint
) {

    public static KnowledgeIndexStatus disabled(int currentChunks) {
        return new KnowledgeIndexStatus(
                "DISABLED",
                false,
                false,
                currentChunks,
                0,
                currentChunks,
                0,
                0,
                "",
                ""
        );
    }
}
