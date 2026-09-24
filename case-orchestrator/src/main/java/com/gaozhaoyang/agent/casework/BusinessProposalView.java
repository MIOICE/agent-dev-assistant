package com.gaozhaoyang.agent.casework;

import java.util.List;

public record BusinessProposalView(
        String caseId,
        String title,
        String summary,
        List<String> affectedModules,
        List<String> acceptanceCriteria,
        List<String> implementationOverview,
        List<String> risksAndAssumptions,
        CaseApproval confirmation
) {
    static BusinessProposalView from(RequirementCase item) {
        return new BusinessProposalView(item.caseId(), item.requirementCard().title(),
                item.currentSolution().summary(), item.requirementCard().affectedModules(),
                item.requirementCard().acceptanceCriteria(),
                item.currentSolution().backendChanges(),
                item.currentSolution().assumptions(), item.approval());
    }
}
