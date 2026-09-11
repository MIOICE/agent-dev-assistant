package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopStateTest {

    @Test
    void shouldRecordCompleteObserveDecideActValidateEvidence() {
        AgentLoopState state = AgentLoopState.initial(3).record(
                AgentLoopAction.RUN_SANDBOX_TEST,
                "补丁尚未验证",
                "使用真实测试代替模型自评",
                "编译失败并获得错误证据",
                "fingerprint-1",
                true
        );

        assertThat(state.consumedSteps()).isEqualTo(1);
        assertThat(state.steps().getFirst().observation()).isEqualTo("补丁尚未验证");
        assertThat(state.steps().getFirst().rationale()).contains("真实测试");
        assertThat(state.steps().getFirst().outcome()).contains("错误证据");
        assertThat(state.hasFingerprint("fingerprint-1")).isTrue();
    }

    @Test
    void shouldTrackConsecutiveNoProgressAndResetAfterProgress() {
        AgentLoopState state = AgentLoopState.initial(4)
                .record(AgentLoopAction.RUN_SANDBOX_TEST,
                        "observe", "reason", "failed", "f1", false)
                .record(AgentLoopAction.REPAIR_PATCH,
                        "observe", "reason", "unchanged", "f2", false);

        assertThat(state.consecutiveNoProgress()).isEqualTo(2);

        AgentLoopState progressed = state.record(
                AgentLoopAction.REPAIR_PATCH,
                "observe", "reason", "changed", "f3", true);
        assertThat(progressed.consecutiveNoProgress()).isZero();
    }

    @Test
    void shouldRefuseActionAfterStepBudgetIsExhausted() {
        AgentLoopState exhausted = AgentLoopState.initial(1).record(
                AgentLoopAction.GENERATE_PATCH,
                "observe", "reason", "generated", "f1", true);

        assertThat(exhausted.exhausted()).isTrue();
        assertThatThrownBy(() -> exhausted.record(
                AgentLoopAction.RUN_SANDBOX_TEST,
                "observe", "reason", "result", "f2", true))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("步骤预算");
    }
}
