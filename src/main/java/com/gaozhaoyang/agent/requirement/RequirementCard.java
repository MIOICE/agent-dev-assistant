package com.gaozhaoyang.agent.requirement;

import java.util.List;

public record RequirementCard(
        String title,
        String background,
        List<String> affectedModules,
        List<String> acceptanceCriteria,
        List<String> missingInformation,
        String priority,
        List<String> risks,
        List<String> references,
        boolean readyForPlanning
) {
}
