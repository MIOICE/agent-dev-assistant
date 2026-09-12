package com.gaozhaoyang.agent.coding;

public enum AgentLoopStopCode {
    NONE,
    WAITING_HUMAN_APPROVAL,
    GOAL_REACHED,
    STEP_BUDGET_EXHAUSTED,
    EXECUTION_BUDGET_EXHAUSTED,
    NO_PROGRESS,
    CANCELLED,
    DEADLINE_EXCEEDED,
    FAILURE
}
