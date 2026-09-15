package com.gaozhaoyang.agent.coding;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DeliveryArtifactVerificationService {

    private static final int MAX_ARCHIVE_BYTES = 3_000_000;
    private static final int MAX_UNCOMPRESSED_BYTES = 4_000_000;
    private static final int MAX_ENTRY_BYTES = 2_000_000;
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_MANIFEST_BYTES = 256_000;
    private static final String MANIFEST = "delivery-manifest.json";
    private static final String SCHEMA = "agent-delivery-v2";
    private static final Predicate<String> SAFE_ENTRY = name ->
            name.equals("APPLYING.md")
                    || name.equals(MANIFEST)
                    || name.startsWith("changes/")
                    || name.startsWith("evidence/")
                    || name.startsWith("project/");

    private final ObjectMapper objectMapper;

    public DeliveryArtifactVerificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DeliveryArtifactVerificationResult verify(byte[] archive, String expectedSha256) {
        byte[] safeArchive = archive == null ? new byte[0] : archive;
        String archiveDigest = sha256(safeArchive);
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        List<String> checks = new ArrayList<>();
        String digestStatus = verifyExpectedDigest(
                archiveDigest, expectedSha256, errors, warnings, checks);

        if (safeArchive.length == 0) {
            errors.add("交付包为空");
        } else if (safeArchive.length > MAX_ARCHIVE_BYTES) {
            errors.add("压缩包超过3MB上传上限");
        }

        Map<String, byte[]> entries = new TreeMap<>();
        long uncompressedBytes = 0;
        if (errors.isEmpty()) {
            try {
                uncompressedBytes = readEntries(safeArchive, entries);
                checks.add("ZIP路径、条目数量与解压规模通过安全检查");
            } catch (ArtifactValidationException exception) {
                errors.add(exception.getMessage());
            } catch (IOException exception) {
                errors.add("无法解析ZIP交付包");
            }
        }

        DeliveryArtifactManifest manifest = null;
        if (errors.isEmpty()) {
            manifest = readManifest(entries, errors);
        }
        if (manifest != null) {
            validateManifest(manifest, entries, errors, checks);
        }

        return new DeliveryArtifactVerificationResult(
                errors.isEmpty(), archiveDigest, digestStatus,
                manifest == null ? "" : text(manifest.schemaVersion()),
                manifest == null ? "" : text(manifest.taskId()),
                manifest == null ? "" : text(manifest.workflowId()),
                Math.max(0, entries.size() - 1), uncompressedBytes,
                checks, warnings, List.copyOf(errors));
    }

    private String verifyExpectedDigest(
            String actual,
            String expected,
            Set<String> errors,
            List<String> warnings,
            List<String> checks
    ) {
        if (expected == null || expected.isBlank()) {
            warnings.add("未提供可信SHA-256：只能确认包内一致性，不能确认发布者身份");
            return "NOT_PROVIDED";
        }
        String normalized = expected.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            errors.add("可信SHA-256格式不正确，应为64位十六进制字符串");
            return "INVALID";
        }
        if (!MessageDigest.isEqual(
                normalized.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            errors.add("压缩包SHA-256与可信摘要不一致");
            return "MISMATCH";
        }
        checks.add("压缩包SHA-256与外部可信摘要一致");
        return "MATCH";
    }

    private long readEntries(byte[] archive, Map<String, byte[]> entries)
            throws IOException {
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    throw invalid("交付包不允许包含目录占位条目");
                }
                String name = entry.getName();
                validateEntryName(name);
                if (entries.size() >= MAX_ENTRIES) {
                    throw invalid("交付包条目数量超过64个");
                }
                if (entries.containsKey(name)) {
                    throw invalid("交付包包含重复条目：" + name);
                }
                byte[] content = readBounded(zip, total);
                total += content.length;
                entries.put(name, content);
                zip.closeEntry();
            }
        }
        if (entries.isEmpty()) {
            throw invalid("ZIP中没有可验证内容");
        }
        return total;
    }

    private byte[] readBounded(ZipInputStream zip, long consumed) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (output.size() + read > MAX_ENTRY_BYTES) {
                throw invalid("单个交付条目解压后超过2MB");
            }
            if (consumed + output.size() + read > MAX_UNCOMPRESSED_BYTES) {
                throw invalid("交付包解压后超过4MB安全上限");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void validateEntryName(String name) {
        if (name == null || name.isBlank()
                || name.startsWith("/")
                || name.contains("\\")
                || name.contains(":")
                || List.of(name.split("/", -1)).contains("..")
                || !SAFE_ENTRY.test(name)) {
            throw invalid("交付包包含非法路径");
        }
    }

    private DeliveryArtifactManifest readManifest(
            Map<String, byte[]> entries,
            Set<String> errors
    ) {
        byte[] content = entries.get(MANIFEST);
        if (content == null) {
            errors.add("缺少delivery-manifest.json");
            return null;
        }
        if (content.length > MAX_MANIFEST_BYTES) {
            errors.add("交付清单超过256KB安全上限");
            return null;
        }
        try {
            return objectMapper.readValue(content, DeliveryArtifactManifest.class);
        } catch (RuntimeException exception) {
            errors.add("delivery-manifest.json格式不正确");
            return null;
        }
    }

    private void validateManifest(
            DeliveryArtifactManifest manifest,
            Map<String, byte[]> entries,
            Set<String> errors,
            List<String> checks
    ) {
        if (!SCHEMA.equals(manifest.schemaVersion())) {
            errors.add("不支持的交付清单版本：" + text(manifest.schemaVersion()));
        }
        if (text(manifest.taskId()).isBlank() || text(manifest.workflowId()).isBlank()) {
            errors.add("交付清单缺少taskId或workflowId");
        }
        if (manifest.verification() == null || !manifest.verification().passed()) {
            errors.add("交付清单没有通过的构建证据");
        }
        validateContentEntries(manifest.contents(), entries, errors);
        validateProjectFiles(manifest.files(), entries, errors);
        validateBuildSummary(manifest, entries, errors);
        if (errors.isEmpty()) {
            checks.add("Manifest身份、Schema与构建证据有效");
            checks.add("所有交付内容的字节数和SHA-256与Manifest一致");
            checks.add("项目文件清单与ZIP中的project目录完全一致");
        }
    }

    private void validateContentEntries(
            List<DeliveryArtifactManifest.FileEntry> declared,
            Map<String, byte[]> entries,
            Set<String> errors
    ) {
        Set<String> expectedNames = new HashSet<>(entries.keySet());
        expectedNames.remove(MANIFEST);
        Set<String> declaredNames = new HashSet<>();
        for (DeliveryArtifactManifest.FileEntry item : declared) {
            if (item == null || !declaredNames.add(text(item.path()))) {
                errors.add("交付清单包含空条目或重复内容路径");
                continue;
            }
            verifyEntry(item, entries.get(item.path()), errors);
        }
        if (!declaredNames.equals(expectedNames)) {
            errors.add("ZIP内容与Manifest内容清单不完全一致");
        }
    }

    private void validateProjectFiles(
            List<DeliveryArtifactManifest.FileEntry> files,
            Map<String, byte[]> entries,
            Set<String> errors
    ) {
        Set<String> expected = new HashSet<>();
        entries.keySet().stream()
                .filter(name -> name.startsWith("project/"))
                .map(name -> name.substring("project/".length()))
                .forEach(expected::add);
        Set<String> declared = new HashSet<>();
        for (DeliveryArtifactManifest.FileEntry file : files) {
            if (file == null || !declared.add(text(file.path()))) {
                errors.add("项目文件清单包含空条目或重复路径");
                continue;
            }
            String path = text(file.path());
            if (path.startsWith("/") || path.contains("\\") || path.contains("..")) {
                errors.add("项目文件清单包含非法路径");
                continue;
            }
            verifyEntry(file, entries.get("project/" + path), errors);
        }
        if (!declared.equals(expected)) {
            errors.add("project目录与项目文件清单不完全一致");
        }
    }

    private void validateBuildSummary(
            DeliveryArtifactManifest manifest,
            Map<String, byte[]> entries,
            Set<String> errors
    ) {
        byte[] summary = entries.get("evidence/build-summary.txt");
        String expected = manifest.verification() == null
                ? ""
                : text(manifest.verification().outputSummarySha256());
        if (summary == null || !sha256(summary).equals(expected)) {
            errors.add("构建摘要与Manifest证据摘要不一致");
        }
    }

    private void verifyEntry(
            DeliveryArtifactManifest.FileEntry expected,
            byte[] actual,
            Set<String> errors
    ) {
        if (actual == null
                || expected.bytes() != actual.length
                || !sha256(actual).equalsIgnoreCase(text(expected.sha256()))) {
            errors.add("交付内容完整性校验失败：" + text(expected.path()));
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前Java运行时不支持SHA-256", exception);
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private ArtifactValidationException invalid(String message) {
        return new ArtifactValidationException(message);
    }

    private static final class ArtifactValidationException extends RuntimeException {
        private ArtifactValidationException(String message) {
            super(message);
        }
    }
}
