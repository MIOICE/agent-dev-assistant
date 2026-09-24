package com.gaozhaoyang.agent.workflow;

public enum WorkflowEventType {
    CREATED,
    REQUIREMENT_ANALYZED,
    CLARIFICATION_REQUESTED,
    CLARIFICATION_RECEIVED,
    KNOWLEDGE_RETRIEVED,
    SOLUTION_GENERATED,
    APPROVED,
    REJECTED,
    FAILED,
    RETRIED
}
