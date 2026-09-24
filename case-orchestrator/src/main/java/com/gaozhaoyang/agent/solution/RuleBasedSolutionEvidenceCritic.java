package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class RuleBasedSolutionEvidenceCritic implements SolutionEvidenceCritic {

    private final SolutionCritiqueAssembler assembler;

    public RuleBasedSolutionEvidenceCritic() {
        this(new SolutionCritiqueAssembler());
    }

    public RuleBasedSolutionEvidenceCritic(SolutionCritiqueAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public SolutionCritiqueReport critique(
            SolutionGroundingReport grounding,
            List<KnowledgeSearchResult> evidence
    ) {
        return critique("RULE_BASED_NOT_EVALUATED", grounding, evidence);
    }

    SolutionCritiqueReport critique(
            String mode,
            SolutionGroundingReport grounding,
            List<KnowledgeSearchResult> evidence
    ) {
        return assembler.assemble(mode, grounding, evidence, List.of());
    }
}
