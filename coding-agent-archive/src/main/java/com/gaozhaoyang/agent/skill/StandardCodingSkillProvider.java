package com.gaozhaoyang.agent.skill;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StandardCodingSkillProvider implements CodingSkillProvider {

    private static final int MAX_ROUTING_CONTEXT_CHARS = 6_000;

    private final AgentSkillCatalog catalog;
    private final EmbeddingModel embeddingModel;
    private final double semanticThreshold;
    private final int maxOptionalSkills;
    private final Map<String, float[]> descriptorVectors = new ConcurrentHashMap<>();

    public StandardCodingSkillProvider(
            AgentSkillCatalog catalog,
            @Qualifier("businessEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${app.skills.semantic-threshold:0.42}") double semanticThreshold,
            @Value("${app.skills.max-optional-skills:2}") int maxOptionalSkills
    ) {
        if (semanticThreshold < -1.0 || semanticThreshold > 1.0) {
            throw new IllegalArgumentException("技能语义阈值必须在-1到1之间");
        }
        if (maxOptionalSkills < 0 || maxOptionalSkills > 4) {
            throw new IllegalArgumentException("可选技能数量必须在0到4之间");
        }
        this.catalog = catalog;
        this.embeddingModel = embeddingModel;
        this.semanticThreshold = semanticThreshold;
        this.maxOptionalSkills = maxOptionalSkills;
    }

    @Override
    public SkillActivation activate(CodingSkillPhase phase, String taskContext) {
        String required = switch (phase) {
            case GENERATION -> "java-code-generation";
            case REPAIR -> "test-failure-repair";
        };
        List<String> selected = new ArrayList<>();
        selected.add(required);
        Map<String, Double> selectedScores = new LinkedHashMap<>();

        String context = truncate(taskContext);
        if (!context.isBlank() && maxOptionalSkills > 0) {
            float[] queryVector = embeddingModel.embed(context);
            catalog.list().stream()
                    .filter(skill -> skill.phases().contains(phase))
                    .filter(skill -> !skill.name().equals(required))
                    .map(skill -> new ScoredSkill(
                            skill.name(), similarity(queryVector,
                                    descriptorVectors.computeIfAbsent(
                                            skill.name(), ignored -> embeddingModel.embed(
                                                    skill.name() + " " + skill.description())))))
                    .filter(skill -> skill.score() >= semanticThreshold)
                    .sorted(Comparator.comparingDouble(ScoredSkill::score).reversed())
                    .limit(maxOptionalSkills)
                    .forEach(skill -> {
                        selected.add(skill.name());
                        selectedScores.put(skill.name(), skill.score());
                    });
        }
        return catalog.activate(selected, selectedScores);
    }

    private String truncate(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= MAX_ROUTING_CONTEXT_CHARS
                ? normalized
                : normalized.substring(0, MAX_ROUTING_CONTEXT_CHARS);
    }

    private double similarity(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalStateException("技能路由向量维度不一致");
        }
        double product = 0.0;
        double leftLength = 0.0;
        double rightLength = 0.0;
        for (int index = 0; index < left.length; index++) {
            product += left[index] * right[index];
            leftLength += left[index] * left[index];
            rightLength += right[index] * right[index];
        }
        if (leftLength == 0.0 || rightLength == 0.0) {
            return 0.0;
        }
        return product / Math.sqrt(leftLength * rightLength);
    }

    private record ScoredSkill(String name, double score) {
    }
}
