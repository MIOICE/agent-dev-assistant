package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgenticEvidenceResearchService implements EvidenceResearcher {

    private final EvidencePlanner planner;
    private final KnowledgeSearcher knowledgeSearcher;
    private final int maxRounds;
    private final int maxQueries;
    private final int maxEvidence;

    public AgenticEvidenceResearchService(
            EvidencePlanner planner,
            KnowledgeSearcher knowledgeSearcher,
            @Value("${app.knowledge.agentic-rag.max-rounds:2}") int maxRounds,
            @Value("${app.knowledge.agentic-rag.max-queries:6}") int maxQueries,
            @Value("${app.knowledge.agentic-rag.max-evidence:8}") int maxEvidence
    ) {
        this.planner = planner;
        this.knowledgeSearcher = knowledgeSearcher;
        this.maxRounds = requireRange(maxRounds, 1, 3, "最大检索轮数");
        this.maxQueries = requireRange(maxQueries, 1, 12, "最大检索次数");
        this.maxEvidence = requireRange(maxEvidence, 1, 20, "最大证据数量");
    }

    @Override
    public EvidenceResearchReport research(
            String originalRequirement,
            String effectiveRequirement,
            RequirementCard card
    ) {
        EvidencePlan plan = planner.plan(effectiveRequirement, card);
        Set<String> unresolved = new LinkedHashSet<>();
        plan.needs().forEach(need -> unresolved.add(need.id()));
        Map<String, KnowledgeSearchResult> evidenceById = new LinkedHashMap<>();
        List<EvidenceQueryAttempt> attempts = new ArrayList<>();
        int queriesUsed = 0;
        int roundsUsed = 0;

        for (int round = 1;
             round <= maxRounds && !unresolved.isEmpty() && queriesUsed < maxQueries;
             round++) {
            roundsUsed = round;
            for (EvidenceNeed need : plan.needs()) {
                if (!unresolved.contains(need.id()) || queriesUsed >= maxQueries) {
                    continue;
                }
                String query = round == 1
                        ? need.query()
                        : rewriteQuery(card, need);
                List<Document> documents = knowledgeSearcher.search(query);
                queriesUsed++;
                List<KnowledgeSearchResult> results = documents.stream()
                        .map(KnowledgeSearchResult::from)
                        .toList();
                boolean satisfied = !results.isEmpty();
                attempts.add(new EvidenceQueryAttempt(
                        round,
                        need.id(),
                        query,
                        results.stream().map(KnowledgeSearchResult::id).toList(),
                        results.stream().mapToDouble(KnowledgeSearchResult::score)
                                .max().orElse(0.0),
                        satisfied
                ));
                results.forEach(result -> evidenceById.merge(
                        result.id(),
                        result,
                        (current, candidate) -> candidate.score() > current.score()
                                ? candidate
                                : current
                ));
                if (satisfied) {
                    unresolved.remove(need.id());
                }
            }
        }

        List<KnowledgeSearchResult> evidence = evidenceById.values().stream()
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
                .limit(maxEvidence)
                .toList();
        boolean sufficient = plan.needs().stream()
                .filter(EvidenceNeed::required)
                .noneMatch(need -> unresolved.contains(need.id()));
        return new EvidenceResearchReport(
                plan.planningMode(),
                plan.needs(),
                attempts,
                evidence,
                List.copyOf(unresolved),
                sufficient,
                roundsUsed,
                queriesUsed,
                maxRounds,
                maxQueries
        );
    }

    private String rewriteQuery(RequirementCard card, EvidenceNeed need) {
        return (card.title() + " "
                + String.join(" ", card.affectedModules()) + " "
                + need.purpose()).trim();
    }

    private int requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "必须在" + min + "到" + max + "之间");
        }
        return value;
    }
}
