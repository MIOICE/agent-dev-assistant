package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Configuration
public class EmbeddingConfiguration {

    @Bean("businessEmbeddingModel")
    public EmbeddingModel businessEmbeddingModel(
            @Value("${app.embedding.tokenizer-uri}")
            String tokenizerUri,
            @Value("${app.embedding.model-uri}")
            String modelUri,
            @Value("${app.embedding.cache-directory}")
            String cacheDirectory,
            @Value("${app.embedding.djl-cache-directory}")
            String djlCacheDirectory,
            @Value("${app.embedding.model-output-name}")
            String modelOutputName
    ) {
        configureCpuRuntime(djlCacheDirectory);

        TransformersEmbeddingModel embeddingModel =
                new TransformersEmbeddingModel();

        embeddingModel.setTokenizerResource(tokenizerUri);
        embeddingModel.setModelResource(modelUri);
        embeddingModel.setResourceCacheDirectory(cacheDirectory);
        embeddingModel.setModelOutputName(modelOutputName);
        embeddingModel.setTokenizerOptions(Map.of(
                "padding", "true",
                "truncation", "true",
                "maxLength", "512"
        ));

        return embeddingModel;
    }

    private void configureCpuRuntime(String djlCacheDirectory) {
        try {
            Files.createDirectories(Path.of(djlCacheDirectory));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法创建DJL缓存目录：" + djlCacheDirectory,
                    exception
            );
        }

        System.setProperty("PYTORCH_FLAVOR", "cpu");
        System.setProperty("DJL_CACHE_DIR", djlCacheDirectory);
        System.setProperty("ENGINE_CACHE_DIR", djlCacheDirectory);
    }
}
