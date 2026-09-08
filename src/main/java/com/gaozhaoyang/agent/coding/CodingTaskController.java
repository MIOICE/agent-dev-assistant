package com.gaozhaoyang.agent.coding;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/coding-tasks")
public class CodingTaskController {

    private final CodingTaskService codingTaskService;

    public CodingTaskController(CodingTaskService codingTaskService) {
        this.codingTaskService = codingTaskService;
    }

    @PostMapping
    public ResponseEntity<CodingTask> create(@RequestParam String workflowId) {
        return ResponseEntity.accepted().body(codingTaskService.submit(workflowId));
    }

    @GetMapping("/{taskId}")
    public CodingTask get(@PathVariable String taskId) {
        return codingTaskService.get(taskId);
    }

    @GetMapping("/workflow/{workflowId}")
    public CodingTask findByWorkflow(@PathVariable String workflowId) {
        return codingTaskService.findByWorkflowId(workflowId)
                .orElseThrow(() -> new CodingTaskException(
                        "当前工作流还没有代码任务：" + workflowId));
    }

    @PostMapping("/{taskId}/approval")
    public CodingTask approve(
            @PathVariable String taskId,
            @Valid @RequestBody CodingTaskApprovalRequest request
    ) {
        return codingTaskService.approve(taskId, request.comment());
    }
}
