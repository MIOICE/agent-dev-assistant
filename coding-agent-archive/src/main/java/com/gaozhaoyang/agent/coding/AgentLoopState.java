package com.gaozhaoyang.agent.coding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 可随 CodingTask Checkpoint 一起持久化的 Agent Loop 状态。
 */
public record AgentLoopState(
        int maxSteps,
        int consumedSteps,
        int consecutiveNoProgress,
        AgentLoopStopCode stopCode,
        String stopReason,
        List<AgentLoopStep> steps
) {
    public AgentLoopState {
        maxSteps = maxSteps <= 0 ? 8 : maxSteps;
        steps = steps == null ? List.of() : List.copyOf(steps);
        consumedSteps = Math.max(consumedSteps, steps.size());
        consecutiveNoProgress = Math.max(consecutiveNoProgress, 0);
        stopCode = stopCode == null ? AgentLoopStopCode.NONE : stopCode;
        stopReason = stopReason == null ? "" : stopReason.trim();
    }

    public static AgentLoopState initial(int maxSteps) {
        return new AgentLoopState(
                maxSteps, 0, 0, AgentLoopStopCode.NONE, "", List.of());
    }

    public boolean exhausted() {
        return consumedSteps >= maxSteps;
    }

    public boolean hasFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        return steps.stream().anyMatch(step -> fingerprint.equals(step.fingerprint()));
    }

    public AgentLoopState record(
            AgentLoopAction action,
            String observation,
            String rationale,
            String outcome,
            String fingerprint,
            boolean progressMade
    ) {
        if (exhausted()) {
            throw new CodingTaskException("Agent Loop步骤预算已耗尽");
        }
        List<AgentLoopStep> updated = new ArrayList<>(steps);
        updated.add(new AgentLoopStep(
                consumedSteps + 1,
                action,
                observation,
                rationale,
                outcome,
                fingerprint,
                progressMade,
                Instant.now()
        ));
        return new AgentLoopState(
                maxSteps,
                consumedSteps + 1,
                progressMade ? 0 : consecutiveNoProgress + 1,
                AgentLoopStopCode.NONE,
                "",
                updated
        );
    }

    public AgentLoopState stop(AgentLoopStopCode code, String reason) {
        return new AgentLoopState(
                maxSteps, consumedSteps, consecutiveNoProgress,
                code, reason, steps);
    }
}
