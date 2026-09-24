package com.gaozhaoyang.agent.skill;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

@Component
public class AgentSkillCatalog {

    private static final Pattern VALID_NAME =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int MAX_DESCRIPTION_CHARS = 1_024;
    private static final int MAX_INSTRUCTIONS_CHARS = 12_000;
    private static final int MAX_ACTIVATED_CONTEXT_CHARS = 24_000;
    private static final Set<String> ALLOWED_TOOL_DECLARATIONS = Set.of(
            "sandbox-read", "sandbox-write", "sandbox-test");

    private final Map<String, IndexedSkill> skills;
    private final Map<String, LongAdder> activationCounts = new ConcurrentHashMap<>();

    @Autowired
    public AgentSkillCatalog(
            @Value("${app.skills.location-pattern:classpath*:agent-skills/*/SKILL.md}")
            String locationPattern,
            @Value("${app.skills.manifest-location:classpath:agent-skills/manifest.sha256}")
            String manifestLocation
    ) {
        this.skills = index(locationPattern, manifestLocation);
    }

    public AgentSkillCatalog(String locationPattern) {
        this(locationPattern, "classpath:agent-skills/manifest.sha256");
    }

    public List<AgentSkillSummary> list() {
        return skills.values().stream()
                .map(skill -> new AgentSkillSummary(
                        skill.name(), skill.description(), skill.phases(), skill.allowedTools(),
                        skill.sha256(), skill.bytes(), true,
                        activationCounts.getOrDefault(skill.name(), new LongAdder()).sum()))
                .sorted(Comparator.comparing(AgentSkillSummary::name))
                .toList();
    }

    public SkillActivation activate(List<String> names) {
        return activate(names, Map.of());
    }

    public SkillActivation activate(List<String> names, Map<String, Double> routingScores) {
        List<String> uniqueNames = names == null
                ? List.of()
                : names.stream().distinct().toList();
        StringBuilder context = new StringBuilder("<agent-skills>\n");
        List<String> activated = new ArrayList<>();
        for (String name : uniqueNames) {
            IndexedSkill skill = skills.get(name);
            if (skill == null) {
                throw new IllegalArgumentException("Agent Skill不存在：" + name);
            }
            String instructions = readInstructions(skill);
            String block = "<skill name=\"" + skill.name() + "\">\n"
                    + instructions + "\n</skill>\n";
            if (context.length() + block.length() > MAX_ACTIVATED_CONTEXT_CHARS) {
                throw new IllegalStateException("本阶段激活的Agent Skills上下文超过安全上限");
            }
            context.append(block);
            activated.add(name);
            activationCounts.computeIfAbsent(name, ignored -> new LongAdder()).increment();
        }
        context.append("</agent-skills>");
        return new SkillActivation(activated, context.toString(), routingScores);
    }

    private Map<String, IndexedSkill> index(String locationPattern, String manifestLocation) {
        try {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(locationPattern);
            if (resources.length == 0) {
                throw new IllegalStateException("未发现任何Agent Skill：" + locationPattern);
            }
            Map<String, String> manifest = readManifest(
                    resolver.getResource(manifestLocation));
            Map<String, IndexedSkill> indexed = new LinkedHashMap<>();
            for (Resource resource : resources) {
                IndexedSkill skill = readMetadata(resource);
                String expectedDigest = manifest.get(skill.name());
                if (expectedDigest == null || !expectedDigest.equals(skill.sha256())) {
                    throw invalid(resource, "SHA-256与受信任清单不一致");
                }
                if (indexed.putIfAbsent(skill.name(), skill) != null) {
                    throw new IllegalStateException("Agent Skill名称重复：" + skill.name());
                }
                activationCounts.put(skill.name(), new LongAdder());
            }
            if (!manifest.keySet().equals(indexed.keySet())) {
                throw new IllegalStateException("技能文件与受信任清单条目不完全一致");
            }
            return Map.copyOf(indexed);
        } catch (IOException exception) {
            throw new IllegalStateException("Agent Skills索引失败", exception);
        }
    }

    private IndexedSkill readMetadata(Resource resource) throws IOException {
        byte[] bytes;
        try (InputStream input = resource.getInputStream()) {
            bytes = input.readAllBytes();
        }
        String sha256 = sha256(canonicalSkillBytes(bytes));
        try (BufferedReader reader = new BufferedReader(new StringReader(
                new String(bytes, StandardCharsets.UTF_8)))) {
            if (!"---".equals(reader.readLine())) {
                throw invalid(resource, "缺少YAML frontmatter");
            }
            String name = null;
            String description = null;
            List<String> allowedTools = new ArrayList<>();
            List<CodingSkillPhase> phases = new ArrayList<>();
            boolean readingAllowedTools = false;
            boolean readingMetadata = false;
            String line;
            boolean closed = false;
            while ((line = reader.readLine()) != null) {
                if ("---".equals(line)) {
                    closed = true;
                    break;
                }
                if (!line.startsWith(" ") && line.startsWith("name:")) {
                    name = scalar(line);
                    readingAllowedTools = false;
                    readingMetadata = false;
                } else if (!line.startsWith(" ") && line.startsWith("description:")) {
                    description = scalar(line);
                    readingAllowedTools = false;
                    readingMetadata = false;
                } else if (!line.startsWith(" ") && line.equals("allowed-tools:")) {
                    readingAllowedTools = true;
                    readingMetadata = false;
                } else if (!line.startsWith(" ") && line.equals("metadata:")) {
                    readingAllowedTools = false;
                    readingMetadata = true;
                } else if (readingAllowedTools && line.stripLeading().startsWith("- ")) {
                    allowedTools.add(line.stripLeading().substring(2).trim());
                } else if (readingMetadata && line.stripLeading().startsWith("phases:")) {
                    for (String phase : scalar(line).split(",")) {
                        phases.add(CodingSkillPhase.valueOf(phase.trim()));
                    }
                } else if (!line.startsWith(" ")) {
                    readingAllowedTools = false;
                    readingMetadata = false;
                }
            }
            if (!closed) {
                throw invalid(resource, "frontmatter未闭合");
            }
            validateMetadata(resource, name, description, phases, allowedTools);
            return new IndexedSkill(
                    name, description, List.copyOf(phases), List.copyOf(allowedTools),
                    sha256, bytes.length, resource);
        }
    }

    private Map<String, String> readManifest(Resource resource) throws IOException {
        if (!resource.exists()) {
            throw new IllegalStateException("Agent Skills受信任清单不存在："
                    + resource.getDescription());
        }
        Map<String, String> manifest = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = line.trim();
                if (normalized.isBlank() || normalized.startsWith("#")) {
                    continue;
                }
                String[] parts = normalized.split("\\s+", 2);
                if (parts.length != 2 || !parts[0].matches("[a-f0-9]{64}")) {
                    throw new IllegalStateException("Agent Skills清单格式无效");
                }
                String path = parts[1].replace('\\', '/').trim();
                String name = path.contains("/")
                        ? path.substring(0, path.indexOf('/')) : path;
                if (manifest.putIfAbsent(name, parts[0]) != null) {
                    throw new IllegalStateException("Agent Skills清单名称重复：" + name);
                }
            }
        }
        return Map.copyOf(manifest);
    }

    private String readInstructions(IndexedSkill skill) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                skill.resource().getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            int delimiters = 0;
            while ((line = reader.readLine()) != null) {
                if ("---".equals(line) && delimiters < 2) {
                    delimiters++;
                    continue;
                }
                if (delimiters == 2) {
                    if (body.length() + line.length() + 1 > MAX_INSTRUCTIONS_CHARS) {
                        throw new IllegalStateException(
                                "Agent Skill正文超过安全上限：" + skill.name());
                    }
                    body.append(line).append('\n');
                }
            }
            String instructions = body.toString().trim();
            if (instructions.isBlank()) {
                throw new IllegalStateException("Agent Skill正文为空：" + skill.name());
            }
            return instructions;
        } catch (IOException exception) {
            throw new IllegalStateException("Agent Skill加载失败：" + skill.name(), exception);
        }
    }

    private void validateMetadata(
            Resource resource,
            String name,
            String description,
            List<CodingSkillPhase> phases,
            List<String> allowedTools
    ) {
        if (name == null || name.length() > 64 || !VALID_NAME.matcher(name).matches()) {
            throw invalid(resource, "name必须是64字符内的小写字母、数字与连字符");
        }
        if (description == null || description.isBlank()
                || description.length() > MAX_DESCRIPTION_CHARS) {
            throw invalid(resource, "description不能为空且不能超过1024字符");
        }
        if (phases.isEmpty()) {
            throw invalid(resource, "metadata.phases不能为空");
        }
        if (!ALLOWED_TOOL_DECLARATIONS.containsAll(allowedTools)) {
            throw invalid(resource, "allowed-tools包含宿主未声明的能力");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private byte[] canonicalSkillBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .getBytes(StandardCharsets.UTF_8);
    }

    private String scalar(String line) {
        String value = line.substring(line.indexOf(':') + 1).trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private IllegalStateException invalid(Resource resource, String reason) {
        return new IllegalStateException("无效Agent Skill " + resource.getDescription()
                + "：" + reason);
    }

    private record IndexedSkill(
            String name,
            String description,
            List<CodingSkillPhase> phases,
            List<String> allowedTools,
            String sha256,
            long bytes,
            Resource resource
    ) {
    }
}
