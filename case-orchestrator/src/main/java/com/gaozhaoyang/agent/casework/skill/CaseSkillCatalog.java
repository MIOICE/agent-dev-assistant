package com.gaozhaoyang.agent.casework.skill;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CaseSkillCatalog {

    private final List<CaseSkill> skills;
    private final String manifestHash;

    public CaseSkillCatalog(
            ResourcePatternResolver resources,
            @Value("${app.cases.skills.location-pattern:classpath*:case-skills/*/SKILL.md}")
            String locationPattern,
            @Value("${app.cases.skills.manifest-location:classpath:case-skills/manifest.sha256}")
            String manifestLocation
    ) {
        try {
            Resource manifestResource = resources.getResource(manifestLocation);
            byte[] manifestBytes = manifestResource.getInputStream().readAllBytes();
            Map<String, String> manifest = parseManifest(new String(manifestBytes, StandardCharsets.UTF_8));
            List<CaseSkill> loaded = new ArrayList<>();
            for (Resource resource : resources.getResources(locationPattern)) {
                byte[] bytes = resource.getInputStream().readAllBytes();
                String path = logicalPath(resource);
                String actual = sha256(bytes);
                String expected = manifest.remove(path);
                if (!actual.equalsIgnoreCase(expected == null ? "" : expected)) {
                    throw new IllegalStateException("Case Skill 完整性校验失败: " + path);
                }
                loaded.add(parseSkill(new String(bytes, StandardCharsets.UTF_8), actual));
            }
            if (!manifest.isEmpty()) {
                throw new IllegalStateException("Case Skill 清单存在未加载条目: " + manifest.keySet());
            }
            this.skills = loaded.stream()
                    .sorted(Comparator.comparing(CaseSkill::name))
                    .toList();
            if (this.skills.isEmpty()) {
                throw new IllegalStateException("至少需要一个已审核的 Case Skill");
            }
            this.manifestHash = sha256(manifestBytes);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载 Case Skills", exception);
        }
    }

    public String instructions(CaseSkillPhase phase) {
        return skills.stream()
                .filter(skill -> skill.phase() == phase)
                .map(skill -> "## " + skill.name() + "@" + skill.version() + "\n" + skill.instructions())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElseThrow(() -> new IllegalStateException("缺少阶段 Skill: " + phase));
    }

    public List<CaseSkill> skills() {
        return skills;
    }

    public String manifestHash() {
        return manifestHash;
    }

    private static Map<String, String> parseManifest(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        content.lines().map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .forEach(line -> {
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length != 2 || result.put(parts[1], parts[0]) != null) {
                        throw new IllegalStateException("非法或重复的 Case Skill 清单条目: " + line);
                    }
                });
        return result;
    }

    private static CaseSkill parseSkill(String content, String hash) {
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            throw new IllegalStateException("Case Skill 缺少 YAML front matter");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException("Case Skill front matter 未闭合");
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        normalized.substring(4, end).lines().forEach(line -> {
            int separator = line.indexOf(':');
            if (separator > 0) {
                metadata.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        });
        return new CaseSkill(required(metadata, "name"), required(metadata, "version"),
                CaseSkillPhase.valueOf(required(metadata, "phase")),
                required(metadata, "description"), normalized.substring(end + 5).trim(), hash);
    }

    private static String required(Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Case Skill 缺少字段: " + key);
        }
        return value;
    }

    private static String logicalPath(Resource resource) throws IOException {
        String value = resource.getURL().toExternalForm().replace('\\', '/');
        int marker = value.lastIndexOf("/case-skills/");
        if (marker < 0) {
            throw new IllegalStateException("无法解析 Case Skill 路径: " + value);
        }
        return value.substring(marker + "/case-skills/".length());
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
