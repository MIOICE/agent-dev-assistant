package com.gaozhaoyang.agent.skill;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final Map<String, IndexedSkill> skills;
    private final Map<String, LongAdder> activationCounts = new ConcurrentHashMap<>();

    public AgentSkillCatalog(
            @Value("${app.skills.location-pattern:classpath*:agent-skills/*/SKILL.md}")
            String locationPattern
    ) {
        this.skills = index(locationPattern);
    }

    public List<AgentSkillSummary> list() {
        return skills.values().stream()
                .map(skill -> new AgentSkillSummary(
                        skill.name(), skill.description(), skill.allowedTools(),
                        activationCounts.getOrDefault(skill.name(), new LongAdder()).sum()))
                .sorted(Comparator.comparing(AgentSkillSummary::name))
                .toList();
    }

    public SkillActivation activate(List<String> names) {
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
        return new SkillActivation(activated, context.toString());
    }

    private Map<String, IndexedSkill> index(String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(locationPattern);
            if (resources.length == 0) {
                throw new IllegalStateException("未发现任何Agent Skill：" + locationPattern);
            }
            Map<String, IndexedSkill> indexed = new LinkedHashMap<>();
            for (Resource resource : resources) {
                IndexedSkill skill = readMetadata(resource);
                if (indexed.putIfAbsent(skill.name(), skill) != null) {
                    throw new IllegalStateException("Agent Skill名称重复：" + skill.name());
                }
                activationCounts.put(skill.name(), new LongAdder());
            }
            return Map.copyOf(indexed);
        } catch (IOException exception) {
            throw new IllegalStateException("Agent Skills索引失败", exception);
        }
    }

    private IndexedSkill readMetadata(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            if (!"---".equals(reader.readLine())) {
                throw invalid(resource, "缺少YAML frontmatter");
            }
            String name = null;
            String description = null;
            List<String> allowedTools = new ArrayList<>();
            boolean readingAllowedTools = false;
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
                } else if (!line.startsWith(" ") && line.startsWith("description:")) {
                    description = scalar(line);
                    readingAllowedTools = false;
                } else if (!line.startsWith(" ") && line.equals("allowed-tools:")) {
                    readingAllowedTools = true;
                } else if (readingAllowedTools && line.stripLeading().startsWith("- ")) {
                    allowedTools.add(line.stripLeading().substring(2).trim());
                } else if (!line.startsWith(" ")) {
                    readingAllowedTools = false;
                }
            }
            if (!closed) {
                throw invalid(resource, "frontmatter未闭合");
            }
            validateMetadata(resource, name, description);
            return new IndexedSkill(name, description, List.copyOf(allowedTools), resource);
        }
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

    private void validateMetadata(Resource resource, String name, String description) {
        if (name == null || name.length() > 64 || !VALID_NAME.matcher(name).matches()) {
            throw invalid(resource, "name必须是64字符内的小写字母、数字与连字符");
        }
        if (description == null || description.isBlank()
                || description.length() > MAX_DESCRIPTION_CHARS) {
            throw invalid(resource, "description不能为空且不能超过1024字符");
        }
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
            List<String> allowedTools,
            Resource resource
    ) {
    }
}
