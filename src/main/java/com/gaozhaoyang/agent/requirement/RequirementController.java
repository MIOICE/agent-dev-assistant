package com.gaozhaoyang.agent.requirement;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    private final RequirementAnalyzer requirementAnalyzer;

    public RequirementController(RequirementAnalyzer requirementAnalyzer) {
        this.requirementAnalyzer = requirementAnalyzer;
    }

    @PostMapping("/analyze")
    public RequirementCard analyze(@Valid @RequestBody RequirementAnalyzeRequest request) {
        return requirementAnalyzer.analyze(request.content());
    }
}
