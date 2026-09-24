package com.gaozhaoyang.agent.skill;

import java.util.List;

public record AgentSkillSummary(
        String name,
        String description,
        List<CodingSkillPhase> phases,
        List<String> allowedTools,
        String sha256,
        long bytes,
        boolean trusted,
        long activationCount
) {
    public AgentSkillSummary {
        phases = phases == null ? List.of() : List.copyOf(phases);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
