package com.gaozhaoyang.agent.coding;

import java.time.Instant;

/**
 * 一次完整的 Observe -> Decide -> Act -> Validate 循环记录。
 */
public record AgentLoopStep(
        int sequence,
        AgentLoopAction action,
        String observation,
        String rationale,
        String outcome,
        String fingerprint,
        boolean progressMade,
        Instant occurredAt
) {
    public AgentLoopStep {
        if (sequence <= 0) {
            throw new IllegalArgumentException("Agent Loop步骤序号必须大于0");
        }
        if (action == null) {
            throw new IllegalArgumentException("Agent Loop行动不能为空");
        }
        observation = normalize(observation);
        rationale = normalize(rationale);
        outcome = normalize(outcome);
        fingerprint = normalize(fingerprint);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
