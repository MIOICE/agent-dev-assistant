package com.gaozhaoyang.agent.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final String application;
    private final String aiMode;
    private final String model;
    private final String apiKey;
    private final String workflowRepository;
    private final String codingTaskRepository;
    private final String embeddingModel;

    public SystemStatusController(
            @Value("${spring.application.name}") String application,
            @Value("${app.ai.mode}") String aiMode,
            @Value("${spring.ai.openai.chat.options.model:未配置}") String model,
            @Value("${DEEPSEEK_API_KEY:}") String apiKey,
            @Value("${app.workflow.repository}") String workflowRepository,
            @Value("${app.coding.repository}") String codingTaskRepository,
            @Value("${app.embedding.model-uri}") String embeddingModel
    ) {
        this.application = application;
        this.aiMode = aiMode;
        this.model = model;
        this.apiKey = apiKey;
        this.workflowRepository = workflowRepository;
        this.codingTaskRepository = codingTaskRepository;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        boolean credentialConfigured = apiKey != null && !apiKey.isBlank();
        return new SystemStatus(
                application,
                aiMode,
                "deepseek".equalsIgnoreCase(aiMode) ? model : "规则引擎（演示模式）",
                credentialConfigured,
                workflowRepository,
                codingTaskRepository,
                shortEmbeddingName(embeddingModel),
                "UP"
        );
    }

    private String shortEmbeddingName(String uri) {
        return uri != null && uri.contains("bge-small-zh-v1.5")
                ? "bge-small-zh-v1.5"
                : "本地向量模型";
    }
}
