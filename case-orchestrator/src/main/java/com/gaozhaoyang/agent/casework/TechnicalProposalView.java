package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.util.List;

public record TechnicalProposalView(
        String caseId,
        CaseStage stage,
        CustomerSystemSnapshot system,
        RequirementCard requirementCard,
        EvidenceBundle evidence,
        TechnicalSolution solution,
        List<SolutionRevision> revisions,
        CaseApproval approval
) {
    static TechnicalProposalView from(RequirementCase item) {
        return new TechnicalProposalView(item.caseId(), item.stage(), item.systemSnapshot(),
                item.requirementCard(), item.evidenceBundle(), item.currentSolution(),
                item.revisions(), item.approval());
    }
}
