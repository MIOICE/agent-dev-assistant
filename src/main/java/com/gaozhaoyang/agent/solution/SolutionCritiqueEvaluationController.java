package com.gaozhaoyang.agent.solution;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solution-critique")
public class SolutionCritiqueEvaluationController {

    private final ClaimEvidenceEvaluator evaluator;

    public SolutionCritiqueEvaluationController(ClaimEvidenceEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @GetMapping("/evaluation")
    public ClaimEvidenceEvaluationReport evaluation() {
        return evaluator.evaluate();
    }
}
