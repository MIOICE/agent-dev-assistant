package com.gaozhaoyang.agent.coding;

import java.util.List;

public record DeliveryArtifactVerificationResult(
        boolean valid,
        String archiveSha256,
        String trustedDigestStatus,
        String schemaVersion,
        String taskId,
        String workflowId,
        int entriesChecked,
        long uncompressedBytes,
        List<String> checks,
        List<String> warnings,
        List<String> errors
) {
    public DeliveryArtifactVerificationResult {
        checks = checks == null ? List.of() : List.copyOf(checks);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
