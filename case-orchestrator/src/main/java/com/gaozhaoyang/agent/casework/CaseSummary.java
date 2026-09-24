package com.gaozhaoyang.agent.casework;

import java.time.Instant;

public record CaseSummary(
        String caseId,
        String title,
        CaseStage stage,
        CustomerSystemSnapshot system,
        Instant updatedAt
) {
    static CaseSummary from(RequirementCase item) {
        String title = item.requirementCard() == null
                ? item.requirement() : item.requirementCard().title();
        return new CaseSummary(item.caseId(), title, item.stage(),
                item.systemSnapshot(), item.updatedAt());
    }
}
