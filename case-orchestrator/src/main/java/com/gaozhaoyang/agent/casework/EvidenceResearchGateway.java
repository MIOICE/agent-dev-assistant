package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.requirement.RequirementCard;

public interface EvidenceResearchGateway {

    ResearchResult research(
            String tenantId,
            CustomerSystemSnapshot system,
            String requirement,
            RequirementCard card,
            String existingRemoteTaskId
    );

    void cancel(String tenantId, String remoteTaskId);

    record ResearchResult(String remoteTaskId, EvidenceBundle bundle, boolean pending) {
    }
}
