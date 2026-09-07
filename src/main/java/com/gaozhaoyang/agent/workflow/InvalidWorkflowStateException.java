package com.gaozhaoyang.agent.workflow;

public class InvalidWorkflowStateException extends RuntimeException {

    public InvalidWorkflowStateException(
            String workflowId,
            WorkflowStage currentStage,
            String operation
    ) {
        super("工作流 " + workflowId + " 当前处于 " + currentStage
                + "，不能执行操作：" + operation);
    }
}
