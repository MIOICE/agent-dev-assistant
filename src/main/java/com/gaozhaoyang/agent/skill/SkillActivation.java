package com.gaozhaoyang.agent.skill;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record SkillActivation(
        List<String> skillNames,
        String instructions,
        Map<String, Double> routingScores
) {

    public SkillActivation {
        skillNames = skillNames == null ? List.of() : List.copyOf(skillNames);
        instructions = instructions == null ? "" : instructions;
        routingScores = routingScores == null ? Map.of() : Map.copyOf(routingScores);
    }

    public SkillActivation(List<String> skillNames, String instructions) {
        this(skillNames, instructions, Map.of());
    }

    public static SkillActivation empty() {
        return new SkillActivation(List.of(), "", Map.of());
    }

    public String routingSummary() {
        return skillNames.stream()
                .map(name -> routingScores.containsKey(name)
                        ? name + "(" + String.format(
                                Locale.ROOT, "%.3f", routingScores.get(name)) + ")"
                        : name + "(required)")
                .reduce((left, right) -> left + "、" + right)
                .orElse("无");
    }
}
