package com.gaozhaoyang.agent.coding;

public enum CodingTaskStage {
    QUEUED,
    GENERATING,
    VERIFYING,
    REPAIRING,
    WAITING_APPROVAL,
    PUBLISHED,
    FAILED
}
