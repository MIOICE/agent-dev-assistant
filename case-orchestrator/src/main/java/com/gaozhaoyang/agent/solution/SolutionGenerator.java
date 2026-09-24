package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import com.gaozhaoyang.agent.requirement.RequirementCard;

import java.util.List;

public interface SolutionGenerator {

    TechnicalSolution generate(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults
    );

    default TechnicalSolution regenerate(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults,
            List<String> reviewerFeedback
    ) {
        return generate(requirementCard, knowledgeResults);
    }
}
