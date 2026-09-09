package com.gaozhaoyang.agent.knowledge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record EvidencePlan(
        String planningMode,
        List<EvidenceNeed> needs
) {

    public EvidencePlan {
        planningMode = planningMode == null || planningMode.isBlank()
                ? "UNKNOWN"
                : planningMode.trim();
        needs = needs == null ? List.of() : List.copyOf(needs);
        if (needs.isEmpty() || needs.size() > 5) {
            throw new IllegalArgumentException("证据计划必须包含1至5个检索需求");
        }
        Set<String> ids = new HashSet<>();
        for (EvidenceNeed need : needs) {
            if (!ids.add(need.id())) {
                throw new IllegalArgumentException("证据需求 ID 重复：" + need.id());
            }
        }
    }

    public EvidencePlan withPlanningMode(String mode) {
        return new EvidencePlan(mode, needs);
    }
}
