package com.gaozhaoyang.agent.workflow;

import java.time.Instant;
import java.util.UUID;

public record WorkflowEvent(
        String eventId,
        WorkflowEventType type,
        String message,
        Instant occurredAt
) {
    public static WorkflowEvent of(WorkflowEventType type, String message) {
        return new WorkflowEvent(
                UUID.randomUUID().toString(),
                type,
                message,
                Instant.now()
        );
    }
}
