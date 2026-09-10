package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;

import java.util.List;

public interface SolutionEvidenceCritic {

    SolutionCritiqueReport critique(
            SolutionGroundingReport grounding,
            List<KnowledgeSearchResult> evidence
    );
}
