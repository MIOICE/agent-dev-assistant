package com.gaozhaoyang.agent.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从一个外部 MES 工作区只读加载经过允许的 Markdown 业务资料。
 * 原始资料不会被复制、修改或提交到本项目。
 */
final class MesKnowledgeDocumentLoader {

    private static final Pattern REQ_DOCUMENT = Pattern.compile(
            "REQ-\\d+-(需求卡片|需求分析|整体方案)\\.md"
    );
    private static final Pattern HEADING = Pattern.compile("^#\\s+(.+)$");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(密码|password|passwd|pwd|api[-_ ]?key|token|secret|密钥)"
                    + "(\\s*(?:[:：=]|为)\\s*)([^\\s,，;；|]+)"
    );
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"
    );
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)"
    );
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)(?<![A-Z0-9_])[A-Z]:[\\\\/][^\\r\\n`|]+"
    );

    LoadResult load(Path configuredRoot, int maxFiles, long maxFileBytes) {
        if (maxFiles <= 0) {
            throw new IllegalArgumentException("外部知识库最大文件数必须大于 0");
        }
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("外部知识库单文件上限必须大于 0");
        }

        Path root = validateRoot(configuredRoot);
        List<Path> candidates;
        try (var paths = Files.walk(root)) {
            candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> path.toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("扫描 MES 外部知识库失败", exception);
        }

        List<BusinessDocument> documents = new ArrayList<>();
        int skipped = 0;
        int sanitized = 0;

        for (Path candidate : candidates) {
            String relativePath = normalize(root.relativize(candidate));
            if (!isAllowed(relativePath)) {
                skipped++;
                continue;
            }
            if (documents.size() >= maxFiles) {
                skipped++;
                continue;
            }

            try {
                if (Files.size(candidate) > maxFileBytes) {
                    skipped++;
                    continue;
                }
                String raw = Files.readString(candidate, StandardCharsets.UTF_8)
                        .replace("\uFEFF", "")
                        .trim();
                if (raw.isBlank() || isDirectoryNode(raw)) {
                    skipped++;
                    continue;
                }

                SanitizedText sanitizedText = sanitize(raw);
                BusinessDocument document = toDocument(
                        relativePath,
                        sanitizedText.text(),
                        sanitizedText.changed()
                );
                documents.add(document);
                if (sanitizedText.changed()) {
                    sanitized++;
                }
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "读取 MES 业务文档失败：" + relativePath,
                        exception
                );
            }
        }

        return new LoadResult(List.copyOf(documents), skipped, sanitized);
    }

    private Path validateRoot(Path configuredRoot) {
        if (configuredRoot == null) {
            throw new IllegalArgumentException("MES 外部知识库目录不能为空");
        }
        try {
            Path root = configuredRoot.toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException(
                        "MES 外部知识库路径不是目录"
                );
            }
            return root;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "MES 外部知识库目录不存在或不可读",
                    exception
            );
        }
    }

    private boolean isAllowed(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md")) {
            return false;
        }
        if (relativePath.startsWith("summary/")) {
            String filename = Path.of(relativePath).getFileName().toString();
            return !"README.md".equalsIgnoreCase(filename)
                    && !"模块目录树.md".equals(filename)
                    && !filename.startsWith("_");
        }
        if (relativePath.startsWith("document/")) {
            String filename = Path.of(relativePath).getFileName().toString();
            return REQ_DOCUMENT.matcher(filename).matches();
        }
        return false;
    }

    private boolean isDirectoryNode(String content) {
        return content.contains("目录节点，无独立 Controller/Action");
    }

    private BusinessDocument toDocument(
            String relativePath,
            String content,
            boolean sanitized
    ) {
        String filename = Path.of(relativePath).getFileName().toString();
        String title = firstHeading(content);
        if (title.isBlank()) {
            title = filename.substring(0, filename.length() - 3);
        }

        String[] segments = relativePath.split("/");
        String module = "需求研发记录";
        String category = "";
        String documentType = requirementDocumentType(filename);
        if (relativePath.startsWith("summary/") && segments.length >= 4) {
            module = segments[1];
            category = segments[2];
            documentType = "BUSINESS_PAGE";
        }

        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(module);
        if (!category.isBlank()) {
            keywords.add(category);
        }
        keywords.add(filename.substring(0, filename.length() - 3));
        keywords.add(title);

        return new BusinessDocument(
                "MES:" + relativePath,
                title,
                List.copyOf(keywords),
                content,
                "external-mes",
                relativePath,
                module,
                category,
                documentType,
                sanitized
        );
    }

    private String requirementDocumentType(String filename) {
        if (filename.endsWith("-需求卡片.md")) {
            return "REQUIREMENT_CARD";
        }
        if (filename.endsWith("-需求分析.md")) {
            return "REQUIREMENT_ANALYSIS";
        }
        if (filename.endsWith("-整体方案.md")) {
            return "TECHNICAL_SOLUTION";
        }
        return "BUSINESS_DOCUMENT";
    }

    private String firstHeading(String content) {
        for (String line : content.lines().toList()) {
            Matcher matcher = HEADING.matcher(line.trim());
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private SanitizedText sanitize(String input) {
        String output = CREDENTIAL.matcher(input)
                .replaceAll("$1$2[REDACTED]");
        output = IPV4.matcher(output).replaceAll("[IP已脱敏]");
        output = EMAIL.matcher(output).replaceAll("[邮箱已脱敏]");
        output = PHONE.matcher(output).replaceAll("[手机号已脱敏]");
        output = WINDOWS_PATH.matcher(output).replaceAll("[本地路径已脱敏]");
        return new SanitizedText(output, !output.equals(input));
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    record LoadResult(
            List<BusinessDocument> documents,
            int skippedDocuments,
            int sanitizedDocuments
    ) {
    }

    private record SanitizedText(String text, boolean changed) {
    }
}
