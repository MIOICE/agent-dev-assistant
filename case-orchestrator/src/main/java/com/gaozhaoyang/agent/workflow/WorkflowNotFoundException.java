package com.gaozhaoyang.agent.workflow;

public class WorkflowNotFoundException extends RuntimeException {

    public WorkflowNotFoundException(String workflowId) {
        super("工作流不存在：" + workflowId);
    }
}
