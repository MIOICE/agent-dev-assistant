package com.gaozhaoyang.agent.casework.skill;

public record CaseSkill(
        String name,
        String version,
        CaseSkillPhase phase,
        String description,
        String instructions,
        String sha256
) {
}
