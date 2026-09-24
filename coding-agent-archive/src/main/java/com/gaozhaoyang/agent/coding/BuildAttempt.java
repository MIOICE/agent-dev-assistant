package com.gaozhaoyang.agent.coding;

import java.time.Instant;

public record BuildAttempt(
        int attempt,
        BuildVerification verification,
        Instant completedAt
) {
}
