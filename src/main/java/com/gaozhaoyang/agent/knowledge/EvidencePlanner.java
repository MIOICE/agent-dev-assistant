package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;

public interface EvidencePlanner {

    EvidencePlan plan(String effectiveRequirement, RequirementCard card);
}
