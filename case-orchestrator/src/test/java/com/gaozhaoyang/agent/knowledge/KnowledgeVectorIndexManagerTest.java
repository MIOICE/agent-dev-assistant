package com.gaozhaoyang.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeVectorIndexManagerTest {

    @Test
    void shouldPersistColdIndexAndWarmLoadWithoutReembedding(
            @TempDir Path root
    ) {
        KnowledgeVectorIndexManager manager = manager();
        List<Document> chunks = List.of(
                document("A", "订单导出需要权限校验"),
                document("B", "批量订单操作需要审计")
        );

        KnowledgeVectorIndex cold = manager.initialize(
                new LocalHashEmbeddingModel(),
                chunks,
                true,
                root,
                "test-model-v1"
        );
        KnowledgeVectorIndex warm = manager.initialize(
                new LocalHashEmbeddingModel(),
                chunks,
                true,
                root,
                "test-model-v1"
        );

        assertThat(cold.status().mode()).isEqualTo("COLD_REBUILD");
        assertThat(cold.status().addedChunks()).isEqualTo(2);
        assertThat(Files.isRegularFile(root.resolve("vector-store.json")))
                .isTrue();
        assertThat(Files.isRegularFile(root.resolve("manifest.json")))
                .isTrue();
        assertThat(warm.status().mode()).isEqualTo("WARM_LOAD");
        assertThat(warm.status().snapshotLoaded()).isTrue();
        assertThat(warm.status().reusedChunks()).isEqualTo(2);
        assertThat(warm.status().addedChunks()).isZero();
        assertThat(warm.vectorStore().similaritySearch(
                SearchRequest.builder()
                        .query("订单导出")
                        .topK(2)
                        .similarityThreshold(0.0)
                        .build()
        )).isNotEmpty();
    }

    @Test
    void shouldOnlyApplyChangedAddedAndRemovedChunks(
            @TempDir Path root
    ) {
        KnowledgeVectorIndexManager manager = manager();
        manager.initialize(
                new LocalHashEmbeddingModel(),
                List.of(
                        document("A", "保持不变"),
                        document("B", "修改前"),
                        document("C", "稍后删除")
                ),
                true,
                root,
                "test-model-v1"
        );

        KnowledgeVectorIndex updated = manager.initialize(
                new LocalHashEmbeddingModel(),
                List.of(
                        document("A", "保持不变"),
                        document("B", "修改后"),
                        document("D", "新增内容")
                ),
                true,
                root,
                "test-model-v1"
        );

        assertThat(updated.status().mode())
                .isEqualTo("INCREMENTAL_UPDATE");
        assertThat(updated.status().currentChunks()).isEqualTo(3);
        assertThat(updated.status().reusedChunks()).isEqualTo(1);
        assertThat(updated.status().addedChunks()).isEqualTo(1);
        assertThat(updated.status().updatedChunks()).isEqualTo(1);
        assertThat(updated.status().removedChunks()).isEqualTo(1);
    }

    @Test
    void shouldRebuildWhenEmbeddingModelIdentityChanges(
            @TempDir Path root
    ) {
        KnowledgeVectorIndexManager manager = manager();
        List<Document> chunks = List.of(document("A", "订单导出"));
        manager.initialize(
                new LocalHashEmbeddingModel(),
                chunks,
                true,
                root,
                "test-model-v1"
        );

        KnowledgeVectorIndex rebuilt = manager.initialize(
                new LocalHashEmbeddingModel(),
                chunks,
                true,
                root,
                "test-model-v2"
        );

        assertThat(rebuilt.status().mode())
                .isEqualTo("INCOMPATIBLE_REBUILD");
        assertThat(rebuilt.status().snapshotLoaded()).isFalse();
        assertThat(rebuilt.status().addedChunks()).isEqualTo(1);
        assertThat(rebuilt.status().modelId()).isEqualTo("test-model-v2");
    }

    @Test
    void shouldBuildInMemoryIndexWhenCacheIsDisabled(
            @TempDir Path root
    ) {
        KnowledgeVectorIndex index = manager().initialize(
                new LocalHashEmbeddingModel(),
                List.of(document("A", "订单导出")),
                false,
                root,
                "test-model-v1"
        );

        assertThat(index.status().mode()).isEqualTo("DISABLED");
        assertThat(index.status().cacheEnabled()).isFalse();
        assertThat(Files.exists(root.resolve("vector-store.json")))
                .isFalse();
    }

    @Test
    void shouldRecoverByRebuildingWhenManifestIsCorrupted(
            @TempDir Path root
    ) throws Exception {
        KnowledgeVectorIndexManager manager = manager();
        List<Document> chunks = List.of(document("A", "订单导出"));
        manager.initialize(
                new LocalHashEmbeddingModel(),
                chunks,
                true,
                root,
                "test-model-v1"
        );
        Files.writeString(root.resolve("manifest.json"), "not-json");

        KnowledgeVectorIndex recovered = manager.initialize(
                new LocalHashEmbeddingModel(),
                chunks,
                true,
                root,
                "test-model-v1"
        );

        assertThat(recovered.status().mode())
                .isEqualTo("RECOVERED_REBUILD");
        assertThat(recovered.status().snapshotLoaded()).isFalse();
        assertThat(recovered.status().addedChunks()).isEqualTo(1);
    }

    private KnowledgeVectorIndexManager manager() {
        return new KnowledgeVectorIndexManager(new ObjectMapper());
    }

    private Document document(String id, String text) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata("sourceId", id)
                .metadata("title", id)
                .metadata("keywords", List.of(id))
                .build();
    }
}
