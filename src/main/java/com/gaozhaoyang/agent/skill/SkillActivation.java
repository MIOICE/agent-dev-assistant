package com.gaozhaoyang.agent.skill;

import java.util.List;

public record SkillActivation(List<String> skillNames, String instructions) {

    public SkillActivation {
        skillNames = skillNames == null ? List.of() : List.copyOf(skillNames);
        instructions = instructions == null ? "" : instructions;
    }

    public static SkillActivation empty() {
        return new SkillActivation(List.of(), "");
    }
}
