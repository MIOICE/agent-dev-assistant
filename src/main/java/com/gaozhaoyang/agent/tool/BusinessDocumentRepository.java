package com.gaozhaoyang.agent.tool;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class BusinessDocumentRepository {

    private static final String DOCUMENT_PATTERN =
            "classpath*:knowledge/business/*.md";

    private final List<BusinessDocument> documents;
    private final KnowledgeIngestionStatistics statistics;

    public BusinessDocumentRepository() {
        this(false, "", 400, 131_072L);
    }

    @Autowired
    public BusinessDocumentRepository(
            @Value("${app.knowledge.external.enabled:false}")
            boolean externalEnabled,
            @Value("${app.knowledge.external.root:}")
            String externalRoot,
            @Value("${app.knowledge.external.max-files:400}")
            int maxExternalFiles,
            @Value("${app.knowledge.external.max-file-bytes:131072}")
            long maxExternalFileBytes
    ) {
        List<BusinessDocument> bundledDocuments = loadBundledDocuments();
        List<BusinessDocument> allDocuments = new ArrayList<>(bundledDocuments);

        MesKnowledgeDocumentLoader.LoadResult externalResult =
                new MesKnowledgeDocumentLoader.LoadResult(List.of(), 0, 0);
        if (externalEnabled) {
            if (externalRoot == null || externalRoot.isBlank()) {
                throw new IllegalArgumentException(
                        "已开启 MES 外部知识库，但 MES_KNOWLEDGE_ROOT 为空"
                );
            }
            externalResult = new MesKnowledgeDocumentLoader().load(
                    Path.of(externalRoot),
                    maxExternalFiles,
                    maxExternalFileBytes
            );
            allDocuments.addAll(externalResult.documents());
        }

        this.documents = List.copyOf(allDocuments);
        this.statistics = buildStatistics(
                externalEnabled,
                bundledDocuments.size(),
                externalResult
        );
    }

    public List<BusinessDocument> findAll() {
        return documents;
    }

    public KnowledgeIngestionStatistics statistics() {
        return statistics;
    }

    private List<BusinessDocument> loadBundledDocuments() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        try {
            Resource[] resources = resolver.getResources(DOCUMENT_PATTERN);

            List<BusinessDocument> loadedDocuments = Arrays.stream(resources)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .map(this::readDocument)
                    .toList();

            if (loadedDocuments.isEmpty()) {
                throw new IllegalStateException(
                        "未在 knowledge/business 目录中找到业务文档"
                );
            }

            return List.copyOf(loadedDocuments);
        } catch (IOException exception) {
            throw new IllegalStateException("加载业务文档失败", exception);
        }
    }

    private BusinessDocument readDocument(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                ))) {
            List<String> lines = reader.lines().toList();
            return parseDocument(resource.getFilename(), lines);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "读取业务文档失败：" + resource.getFilename(),
                    exception
            );
        }
    }

    private BusinessDocument parseDocument(
            String filename,
            List<String> lines
    ) {
        if (lines.isEmpty() || !"---".equals(lines.getFirst().trim())) {
            throw invalidDocument(filename, "缺少开头的 ---");
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        int contentStartIndex = -1;

        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();

            if ("---".equals(line)) {
                contentStartIndex = index + 1;
                break;
            }

            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                throw invalidDocument(filename, "元数据格式错误：" + line);
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            metadata.put(key, value);
        }

        if (contentStartIndex < 0) {
            throw invalidDocument(filename, "缺少元数据结束标记 ---");
        }

        String id = requiredMetadata(metadata, "id", filename);
        String title = requiredMetadata(metadata, "title", filename);
        String keywordText = requiredMetadata(
                metadata,
                "keywords",
                filename
        );
        String content = String.join(
                System.lineSeparator(),
                lines.subList(contentStartIndex, lines.size())
        ).trim();

        if (content.isBlank()) {
            throw invalidDocument(filename, "正文不能为空");
        }

        List<String> keywords = Arrays.stream(keywordText.split("[,，]"))
                .map(String::trim)
                .filter(keyword -> !keyword.isBlank())
                .toList();

        if (keywords.isEmpty()) {
            throw invalidDocument(filename, "keywords 不能为空");
        }

        return new BusinessDocument(
                id,
                title,
                keywords,
                content,
                "bundled",
                "knowledge/business/" + filename,
                "通用规范",
                "",
                "BUSINESS_POLICY",
                false
        );
    }

    private KnowledgeIngestionStatistics buildStatistics(
            boolean externalEnabled,
            int bundledCount,
            MesKnowledgeDocumentLoader.LoadResult externalResult
    ) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (BusinessDocument document : documents) {
            byType.merge(document.documentType(), 1, Integer::sum);
        }
        return new KnowledgeIngestionStatistics(
                externalEnabled,
                externalEnabled && !externalResult.documents().isEmpty(),
                bundledCount,
                externalResult.documents().size(),
                externalResult.skippedDocuments(),
                externalResult.sanitizedDocuments(),
                byType
        );
    }

    private String requiredMetadata(
            Map<String, String> metadata,
            String key,
            String filename
    ) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw invalidDocument(filename, "缺少元数据：" + key);
        }
        return value;
    }

    private IllegalStateException invalidDocument(
            String filename,
            String reason
    ) {
        return new IllegalStateException(
                "业务文档格式错误 [" + filename + "]：" + reason
        );
    }
}
