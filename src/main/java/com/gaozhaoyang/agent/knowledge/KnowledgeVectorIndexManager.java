package com.gaozhaoyang.agent.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class KnowledgeVectorIndexManager {

    private static final int SCHEMA_VERSION = 1;
    private static final String STORE_FILENAME = "vector-store.json";
    private static final String MANIFEST_FILENAME = "manifest.json";

    private final ObjectMapper objectMapper;

    public KnowledgeVectorIndexManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    KnowledgeVectorIndex initialize(
            EmbeddingModel embeddingModel,
            List<Document> currentChunks,
            boolean cacheEnabled,
            Path configuredRoot,
            String modelId
    ) {
        if (!cacheEnabled) {
            SimpleVectorStore vectorStore = newStore(embeddingModel);
            vectorStore.add(currentChunks);
            return new KnowledgeVectorIndex(
                    vectorStore,
                    KnowledgeIndexStatus.disabled(currentChunks.size())
            );
        }
        if (configuredRoot == null) {
            throw new IllegalArgumentException("知识索引缓存目录不能为空");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("知识索引模型标识不能为空");
        }

        Path root = prepareRoot(configuredRoot);
        Path storeFile = root.resolve(STORE_FILENAME);
        Path manifestFile = root.resolve(MANIFEST_FILENAME);
        Map<String, String> currentFingerprints = fingerprints(currentChunks);
        String corpusFingerprint = corpusFingerprint(currentFingerprints);

        if (!Files.isRegularFile(storeFile)
                || !Files.isRegularFile(manifestFile)) {
            return rebuild(
                    embeddingModel,
                    currentChunks,
                    root,
                    modelId,
                    currentFingerprints,
                    corpusFingerprint,
                    "COLD_REBUILD"
            );
        }

        try {
            KnowledgeIndexManifest manifest = objectMapper.readValue(
                    manifestFile.toFile(),
                    KnowledgeIndexManifest.class
            );
            if (!compatible(manifest, modelId)) {
                return rebuild(
                        embeddingModel,
                        currentChunks,
                        root,
                        modelId,
                        currentFingerprints,
                        corpusFingerprint,
                        "INCOMPATIBLE_REBUILD"
                );
            }

            SimpleVectorStore vectorStore = newStore(embeddingModel);
            vectorStore.load(storeFile.toFile());
            return updateLoadedIndex(
                    vectorStore,
                    currentChunks,
                    root,
                    manifest,
                    modelId,
                    currentFingerprints,
                    corpusFingerprint
            );
        } catch (RuntimeException exception) {
            return rebuild(
                    embeddingModel,
                    currentChunks,
                    root,
                    modelId,
                    currentFingerprints,
                    corpusFingerprint,
                    "RECOVERED_REBUILD"
            );
        }
    }

    private KnowledgeVectorIndex updateLoadedIndex(
            SimpleVectorStore vectorStore,
            List<Document> currentChunks,
            Path root,
            KnowledgeIndexManifest manifest,
            String modelId,
            Map<String, String> currentFingerprints,
            String corpusFingerprint
    ) {
        Map<String, String> previous = manifest.chunkFingerprints();
        Map<String, Document> chunksById = new LinkedHashMap<>();
        currentChunks.forEach(document ->
                chunksById.put(document.getId(), document)
        );

        List<String> removedIds = previous.keySet().stream()
                .filter(id -> !currentFingerprints.containsKey(id))
                .sorted()
                .toList();
        List<String> updatedIds = currentFingerprints.entrySet().stream()
                .filter(entry -> previous.containsKey(entry.getKey()))
                .filter(entry -> !entry.getValue().equals(
                        previous.get(entry.getKey())
                ))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        List<String> addedIds = currentFingerprints.keySet().stream()
                .filter(id -> !previous.containsKey(id))
                .sorted()
                .toList();

        if (removedIds.isEmpty()
                && updatedIds.isEmpty()
                && addedIds.isEmpty()) {
            return new KnowledgeVectorIndex(
                    vectorStore,
                    status(
                            "WARM_LOAD",
                            true,
                            currentChunks.size(),
                            currentChunks.size(),
                            0,
                            0,
                            0,
                            modelId,
                            corpusFingerprint
                    )
            );
        }

        List<String> deleteIds = new ArrayList<>(removedIds);
        deleteIds.addAll(updatedIds);
        if (!deleteIds.isEmpty()) {
            vectorStore.delete(deleteIds);
        }

        List<Document> upserts = new ArrayList<>();
        updatedIds.forEach(id -> upserts.add(chunksById.get(id)));
        addedIds.forEach(id -> upserts.add(chunksById.get(id)));
        if (!upserts.isEmpty()) {
            vectorStore.add(upserts);
        }

        persist(
                vectorStore,
                root,
                manifest(modelId, corpusFingerprint, currentFingerprints)
        );
        int reused = currentChunks.size()
                - updatedIds.size()
                - addedIds.size();
        return new KnowledgeVectorIndex(
                vectorStore,
                status(
                        "INCREMENTAL_UPDATE",
                        true,
                        currentChunks.size(),
                        Math.max(reused, 0),
                        addedIds.size(),
                        updatedIds.size(),
                        removedIds.size(),
                        modelId,
                        corpusFingerprint
                )
        );
    }

    private KnowledgeVectorIndex rebuild(
            EmbeddingModel embeddingModel,
            List<Document> currentChunks,
            Path root,
            String modelId,
            Map<String, String> currentFingerprints,
            String corpusFingerprint,
            String mode
    ) {
        SimpleVectorStore vectorStore = newStore(embeddingModel);
        vectorStore.add(currentChunks);
        persist(
                vectorStore,
                root,
                manifest(modelId, corpusFingerprint, currentFingerprints)
        );
        return new KnowledgeVectorIndex(
                vectorStore,
                status(
                        mode,
                        false,
                        currentChunks.size(),
                        0,
                        currentChunks.size(),
                        0,
                        0,
                        modelId,
                        corpusFingerprint
                )
        );
    }

    private KnowledgeIndexStatus status(
            String mode,
            boolean snapshotLoaded,
            int currentChunks,
            int reusedChunks,
            int addedChunks,
            int updatedChunks,
            int removedChunks,
            String modelId,
            String corpusFingerprint
    ) {
        return new KnowledgeIndexStatus(
                mode,
                true,
                snapshotLoaded,
                currentChunks,
                reusedChunks,
                addedChunks,
                updatedChunks,
                removedChunks,
                modelId,
                shortFingerprint(corpusFingerprint)
        );
    }

    private boolean compatible(
            KnowledgeIndexManifest manifest,
            String modelId
    ) {
        return manifest != null
                && manifest.schemaVersion() == SCHEMA_VERSION
                && modelId.equals(manifest.modelId())
                && manifest.chunkFingerprints() != null;
    }

    private KnowledgeIndexManifest manifest(
            String modelId,
            String corpusFingerprint,
            Map<String, String> chunkFingerprints
    ) {
        return new KnowledgeIndexManifest(
                SCHEMA_VERSION,
                modelId,
                corpusFingerprint,
                chunkFingerprints
        );
    }

    private SimpleVectorStore newStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    private Path prepareRoot(Path configuredRoot) {
        try {
            Path root = configuredRoot.toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
                throw new IllegalArgumentException(
                        "知识索引缓存路径必须是普通目录"
                );
            }
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建知识索引缓存目录", exception);
        }
    }

    private Map<String, String> fingerprints(List<Document> documents) {
        Map<String, String> result = new LinkedHashMap<>();
        documents.stream()
                .sorted(Comparator.comparing(Document::getId))
                .forEach(document -> result.put(
                        document.getId(),
                        fingerprint(document)
                ));
        if (result.size() != documents.size()) {
            throw new IllegalStateException("知识Chunk ID存在重复");
        }
        return Map.copyOf(result);
    }

    private String fingerprint(Document document) {
        MessageDigest digest = sha256();
        update(digest, document.getId());
        update(digest, document.getText());
        new TreeMap<>(document.getMetadata()).forEach((key, value) -> {
            update(digest, key);
            update(digest, String.valueOf(value));
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private String corpusFingerprint(Map<String, String> chunkFingerprints) {
        MessageDigest digest = sha256();
        chunkFingerprints.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    update(digest, entry.getKey());
                    update(digest, entry.getValue());
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM不支持SHA-256", exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private String shortFingerprint(String fingerprint) {
        return fingerprint.substring(0, Math.min(fingerprint.length(), 12));
    }

    private void persist(
            SimpleVectorStore vectorStore,
            Path root,
            KnowledgeIndexManifest manifest
    ) {
        Path storeTemp = null;
        Path manifestTemp = null;
        try {
            storeTemp = root.resolve(
                    "vector-store-" + UUID.randomUUID() + ".tmp"
            );
            manifestTemp = root.resolve(
                    "manifest-" + UUID.randomUUID() + ".tmp"
            );
            vectorStore.save(storeTemp.toFile());
            objectMapper.writeValue(manifestTemp.toFile(), manifest);
            replace(storeTemp, root.resolve(STORE_FILENAME));
            storeTemp = null;
            replace(manifestTemp, root.resolve(MANIFEST_FILENAME));
            manifestTemp = null;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("保存知识向量索引失败", exception);
        } finally {
            deleteTemporaryFile(storeTemp);
            deleteTemporaryFile(manifestTemp);
        }
    }

    private void replace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // 临时缓存可在下次启动时覆盖，不影响已发布索引。
        }
    }
}
