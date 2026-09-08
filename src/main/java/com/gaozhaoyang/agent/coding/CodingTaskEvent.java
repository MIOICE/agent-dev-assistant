package com.gaozhaoyang.agent.coding;

import java.time.Instant;

public record CodingTaskEvent(
        String type,
        String message,
        Instant occurredAt
) {
    public static CodingTaskEvent of(String type, String message) {
        return new CodingTaskEvent(type, message, Instant.now());
    }
}
