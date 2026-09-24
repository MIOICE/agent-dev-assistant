package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RequirementCase(
        String caseId,
        String tenantId,
        String createdBy,
        String requirement,
        CustomerSystemSnapshot systemSnapshot,
        CaseStage stage,
        RequirementCard requirementCard,
        Map<String, String> clarificationAnswers,
        String remoteTaskId,
        EvidenceBundle evidenceBundle,
        TechnicalSolution currentSolution,
        List<SolutionRevision> revisions,
        CaseApproval approval,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public RequirementCase {
        clarificationAnswers = clarificationAnswers == null
                ? Map.of() : Map.copyOf(new LinkedHashMap<>(clarificationAnswers));
        revisions = revisions == null ? List.of() : List.copyOf(revisions);
    }

    public static RequirementCase create(
            String caseId,
            String tenantId,
            String createdBy,
            String requirement,
            CustomerSystemSnapshot systemSnapshot,
            Instant now
    ) {
        return new RequirementCase(caseId, tenantId, createdBy, requirement, systemSnapshot,
                CaseStage.ANALYZING_REQUIREMENT, null, Map.of(), null, null, null,
                List.of(), null, null, null, now, now, 0);
    }

    public RequirementCase progress(
            CaseStage nextStage,
            RequirementCard nextCard,
            String nextRemoteTaskId,
            EvidenceBundle nextEvidence,
            TechnicalSolution nextSolution,
            List<SolutionRevision> nextRevisions,
            CaseApproval nextApproval,
            String nextFailureCode,
            String nextFailureMessage,
            Instant now
    ) {
        return new RequirementCase(caseId, tenantId, createdBy, requirement, systemSnapshot,
                nextStage, nextCard, clarificationAnswers, nextRemoteTaskId, nextEvidence,
                nextSolution, nextRevisions, nextApproval, nextFailureCode, nextFailureMessage,
                createdAt, now, version);
    }

    public RequirementCase withClarifications(Map<String, String> answers, Instant now) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(clarificationAnswers);
        merged.putAll(answers);
        return new RequirementCase(caseId, tenantId, createdBy, requirement, systemSnapshot,
                CaseStage.ANALYZING_REQUIREMENT, requirementCard, merged, remoteTaskId,
                evidenceBundle, currentSolution, revisions, approval, null, null,
                createdAt, now, version);
    }

    public RequirementCase storedAtVersion(long storedVersion) {
        return new RequirementCase(caseId, tenantId, createdBy, requirement, systemSnapshot,
                stage, requirementCard, clarificationAnswers, remoteTaskId, evidenceBundle,
                currentSolution, revisions, approval, failureCode, failureMessage,
                createdAt, updatedAt, storedVersion);
    }
}
