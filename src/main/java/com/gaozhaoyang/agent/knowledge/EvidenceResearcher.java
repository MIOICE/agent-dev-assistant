package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;

public interface EvidenceResearcher {

    EvidenceResearchReport research(
            String originalRequirement,
            String effectiveRequirement,
            RequirementCard card
    );
}
