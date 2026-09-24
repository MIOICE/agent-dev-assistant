package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;

import java.util.List;

public class SingleQueryEvidenceResearcher implements EvidenceResearcher {

    private final KnowledgeSearcher knowledgeSearcher;

    public SingleQueryEvidenceResearcher(KnowledgeSearcher knowledgeSearcher) {
        this.knowledgeSearcher = knowledgeSearcher;
    }

    @Override
    public EvidenceResearchReport research(
            String originalRequirement,
            String effectiveRequirement,
            RequirementCard card
    ) {
        String modules = String.join(" ", card.affectedModules());
        String query = modules.isBlank()
                ? originalRequirement
                : originalRequirement + " " + modules;
        EvidenceNeed need = new EvidenceNeed(
                "LEGACY_QUERY", query, "兼容单次检索调用", true);
        List<KnowledgeSearchResult> results = knowledgeSearcher.search(query).stream()
                .map(KnowledgeSearchResult::from)
                .toList();
        EvidenceQueryAttempt attempt = new EvidenceQueryAttempt(
                1,
                need.id(),
                query,
                results.stream().map(KnowledgeSearchResult::id).toList(),
                results.stream().mapToDouble(KnowledgeSearchResult::score)
                        .max().orElse(0.0),
                !results.isEmpty()
        );
        return new EvidenceResearchReport(
                "SINGLE_QUERY_COMPATIBILITY",
                List.of(need),
                List.of(attempt),
                results,
                results.isEmpty() ? List.of(need.id()) : List.of(),
                !results.isEmpty(),
                1,
                1,
                1,
                1
        );
    }
}
