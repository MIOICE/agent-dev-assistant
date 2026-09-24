package com.gaozhaoyang.agent.solution;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/solution-critique")
public class SolutionCritiqueEvaluationController {

    private final ClaimEvidenceEvaluationRunService runService;

    public SolutionCritiqueEvaluationController(ClaimEvidenceEvaluationRunService runService) {
        this.runService = runService;
    }

    @GetMapping("/evaluation")
    public ResponseEntity<ClaimEvidenceEvaluationReport> evaluation() {
        return ResponseEntity.of(runService.latest().map(ClaimEvidenceEvaluationRun::report));
    }

    @PostMapping("/evaluation/runs")
    public ResponseEntity<ClaimEvidenceEvaluationRun> runEvaluation() {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(runService.runAndRecord());
    }

    @GetMapping("/evaluation/runs")
    public List<ClaimEvidenceEvaluationRun> evaluationHistory(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return runService.history(limit);
    }

    @GetMapping("/evaluation/runs/latest")
    public ResponseEntity<ClaimEvidenceEvaluationRun> latestEvaluation() {
        return ResponseEntity.of(runService.latest());
    }
}
