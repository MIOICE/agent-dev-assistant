package com.gaozhaoyang.agent.solution;

import java.util.List;

public record TechnicalSolution(
        String summary,
        List<String> backendChanges,
        List<String> databaseChanges,
        List<String> apiDesign,
        List<String> securityControls,
        List<String> performanceStrategy,
        List<String> testPlan,
        List<String> rollbackPlan,
        List<String> assumptions
) {
}
