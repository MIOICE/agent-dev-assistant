package com.gaozhaoyang.agent.knowledge;

import java.util.Map;

record KnowledgeIndexManifest(
        int schemaVersion,
        String modelId,
        String corpusFingerprint,
        Map<String, String> chunkFingerprints
) {

    KnowledgeIndexManifest {
        chunkFingerprints = Map.copyOf(chunkFingerprints);
    }
}
