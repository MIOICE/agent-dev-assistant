package com.gaozhaoyang.agent.coding;

public record CodingTaskRuntimeStatus(
        int corePoolSize,
        int activeThreads,
        int poolSize,
        int queuedTasks,
        int queueRemainingCapacity,
        int runningTasks,
        long maxTaskRuntimeMs
) {
}
