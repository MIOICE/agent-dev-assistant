package com.gaozhaoyang.agent.common;

public record SystemStatus(
        String application,
        String aiMode,
        String model,
        boolean modelCredentialConfigured,
        String workflowRepository,
        String embeddingModel,
        String status
) {
}
