package com.gaozhaoyang.agent.solution;

import java.time.Instant;

public record ClaimEvidenceEvaluationRun(
        String runId,
        String aiMode,
        String modelId,
        String promptVersion,
        String datasetVersion,
        String datasetFingerprint,
        ClaimEvidenceEvaluationReport report,
        EvaluationGateDecision gate,
        EvaluationBaselineComparison baselineComparison,
        Instant createdAt
) {
    public ClaimEvidenceEvaluationRun {
        if (runId == null || !runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("评测运行ID格式不合法");
        }
        aiMode = requireText(aiMode, "aiMode");
        modelId = requireText(modelId, "modelId");
        promptVersion = requireText(promptVersion, "promptVersion");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        datasetFingerprint = requireText(datasetFingerprint, "datasetFingerprint");
        if (report == null || gate == null || baselineComparison == null) {
            throw new IllegalArgumentException("评测运行结果不能为空");
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
