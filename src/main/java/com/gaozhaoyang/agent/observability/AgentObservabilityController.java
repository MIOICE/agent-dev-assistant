package com.gaozhaoyang.agent.observability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class AgentObservabilityController {

    private final AgentObservabilityService observabilityService;

    public AgentObservabilityController(AgentObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping("/traces/{workflowId}")
    public UnifiedAgentTraceReport trace(@PathVariable String workflowId) {
        return observabilityService.trace(workflowId);
    }
}
