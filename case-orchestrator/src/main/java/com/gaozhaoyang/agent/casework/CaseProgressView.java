package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.requirement.ClarificationQuestion;

import java.time.Instant;
import java.util.List;

public record CaseProgressView(
        String caseId,
        CaseStage stage,
        String title,
        List<ClarificationQuestion> clarificationQuestions,
        List<String> unresolvedEvidence,
        int evidenceCount,
        boolean businessProposalAvailable,
        String failureCode,
        String failureMessage,
        Instant updatedAt
) {
    static CaseProgressView from(RequirementCase item) {
        return new CaseProgressView(
                item.caseId(), item.stage(),
                item.requirementCard() == null ? item.requirement() : item.requirementCard().title(),
                item.requirementCard() == null ? List.of() : item.requirementCard().clarificationQuestions(),
                item.evidenceBundle() == null ? List.of() : item.evidenceBundle().unresolved(),
                item.evidenceBundle() == null ? 0 : item.evidenceBundle().evidence().size(),
                item.stage() == CaseStage.PUBLISHED,
                item.failureCode(), item.failureMessage(), item.updatedAt());
    }
}
