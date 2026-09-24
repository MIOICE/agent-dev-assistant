package com.gaozhaoyang.agent.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final RequirementWorkflowService requirementWorkflowService;

    @Value("${app.cases.legacy-workflows-read-only:true}")
    private boolean legacyWorkflowsReadOnly;

    public WorkflowController(RequirementWorkflowService requirementWorkflowService) {
        this.requirementWorkflowService = requirementWorkflowService;
    }

    @PostMapping
    public WorkflowState start(@Valid @RequestBody WorkflowStartRequest request) {
        ensureLegacyWritesAllowed();
        return requirementWorkflowService.start(request.requirement());
    }

    @GetMapping
    public WorkflowPage list(
            @RequestParam(required = false) WorkflowStage stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return requirementWorkflowService.list(stage, page, size);
    }

    @GetMapping("/{workflowId}")
    public WorkflowState get(@PathVariable String workflowId) {
        return requirementWorkflowService.get(workflowId);
    }

    @GetMapping("/metrics")
    public WorkflowMetrics metrics() {
        return requirementWorkflowService.metrics();
    }

    @GetMapping("/{workflowId}/trace")
    public WorkflowTraceReport trace(@PathVariable String workflowId) {
        return requirementWorkflowService.trace(workflowId);
    }

    @PostMapping("/{workflowId}/clarifications")
    public WorkflowState clarify(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowClarificationRequest request
    ) {
        ensureLegacyWritesAllowed();
        return requirementWorkflowService.clarify(
                workflowId,
                request.clarification()
        );
    }

    @PostMapping("/{workflowId}/clarifications/recommendations")
    public WorkflowState acceptRecommendedClarifications(@PathVariable String workflowId) {
        ensureLegacyWritesAllowed();
        return requirementWorkflowService.acceptRecommendedClarifications(workflowId);
    }

    @PostMapping("/{workflowId}/approval")
    public WorkflowState approve(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowApprovalRequest request
    ) {
        ensureLegacyWritesAllowed();
        return requirementWorkflowService.approve(workflowId, request.comment());
    }

    @PostMapping("/{workflowId}/rejection")
    public WorkflowState reject(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowRejectionRequest request
    ) {
        ensureLegacyWritesAllowed();
        return requirementWorkflowService.reject(workflowId, request.feedback());
    }

    @PostMapping("/{workflowId}/retry")
    public WorkflowState retry(@PathVariable String workflowId) {
        ensureLegacyWritesAllowed();
        return requirementWorkflowService.retry(workflowId);
    }

    private void ensureLegacyWritesAllowed() {
        if (legacyWorkflowsReadOnly) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "旧工作流只读保留，请使用 /api/cases 创建新的实施方案 Case");
        }
    }
}
