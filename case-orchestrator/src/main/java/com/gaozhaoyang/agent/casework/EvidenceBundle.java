package com.gaozhaoyang.agent.casework;

import java.util.List;

public record EvidenceBundle(
        String schemaVersion,
        String tenantId,
        String systemId,
        String systemVersion,
        List<EvidenceItem> evidence,
        List<EvidenceCoverage> coverage,
        List<String> unresolved,
        boolean sufficient
) {
    public EvidenceBundle {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        coverage = coverage == null ? List.of() : List.copyOf(coverage);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
    }

    public record EvidenceItem(
            String chunkId,
            String documentId,
            String title,
            String source,
            Integer chunkIndex,
            String content,
            Double score,
            String spaceKey,
            String systemVersion,
            String retrievalMode,
            List<String> retrievalSources,
            Double semanticScore,
            Double lexicalScore
    ) {
        public EvidenceItem {
            retrievalSources = retrievalSources == null ? List.of() : List.copyOf(retrievalSources);
        }
    }

    public record EvidenceCoverage(String query, List<String> hitIds) {
        public EvidenceCoverage {
            hitIds = hitIds == null ? List.of() : List.copyOf(hitIds);
        }
    }
}
