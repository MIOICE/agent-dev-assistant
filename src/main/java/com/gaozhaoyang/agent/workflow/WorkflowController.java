package com.gaozhaoyang.agent.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final RequirementWorkflowService requirementWorkflowService;

    public WorkflowController(RequirementWorkflowService requirementWorkflowService) {
        this.requirementWorkflowService = requirementWorkflowService;
    }

    @PostMapping
    public WorkflowState start(@Valid @RequestBody WorkflowStartRequest request) {
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

    @PostMapping("/{workflowId}/clarifications")
    public WorkflowState clarify(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowClarificationRequest request
    ) {
        return requirementWorkflowService.clarify(
                workflowId,
                request.clarification()
        );
    }

    @PostMapping("/{workflowId}/approval")
    public WorkflowState approve(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowApprovalRequest request
    ) {
        return requirementWorkflowService.approve(workflowId, request.comment());
    }

    @PostMapping("/{workflowId}/rejection")
    public WorkflowState reject(
            @PathVariable String workflowId,
            @Valid @RequestBody WorkflowRejectionRequest request
    ) {
        return requirementWorkflowService.reject(workflowId, request.feedback());
    }

    @PostMapping("/{workflowId}/retry")
    public WorkflowState retry(@PathVariable String workflowId) {
        return requirementWorkflowService.retry(workflowId);
    }
}
