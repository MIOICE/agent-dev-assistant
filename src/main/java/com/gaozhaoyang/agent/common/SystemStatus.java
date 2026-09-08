package com.gaozhaoyang.agent.common;

public record SystemStatus(
        String application,
        String aiMode,
        String model,
        boolean modelCredentialConfigured,
        String workflowRepository,
        String codingTaskRepository,
        String embeddingModel,
        String status
) {
}
