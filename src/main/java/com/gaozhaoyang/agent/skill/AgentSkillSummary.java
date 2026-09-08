package com.gaozhaoyang.agent.skill;

import java.util.List;

public record AgentSkillSummary(
        String name,
        String description,
        List<String> allowedTools,
        long activationCount
) {
    public AgentSkillSummary {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
