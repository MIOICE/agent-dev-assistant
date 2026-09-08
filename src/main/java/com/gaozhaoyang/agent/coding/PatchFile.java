package com.gaozhaoyang.agent.coding;

public record PatchFile(
        String relativePath,
        String purpose,
        String operation,
        String sha256,
        long bytes,
        String unifiedDiff
) {
}
