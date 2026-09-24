package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;

import java.time.Instant;
import java.util.List;

public record CodingTask(
        String taskId,
        String workflowId,
        WorkflowState workflowSnapshot,
        CodingTaskStage stage,
        String summary,
        AutonomyBudget budget,
        int consumedFiles,
        long consumedBytes,
        int consumedBuildExecutions,
        long consumedDurationMs,
        int repairAttempts,
        List<String> activatedSkills,
        List<PatchFile> patches,
        BuildVerification verification,
        List<BuildAttempt> buildAttempts,
        List<CodingTaskEvent> events,
        AgentLoopState agentLoop,
        String workspaceId,
        String approvedOutputPath,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public CodingTask {
        activatedSkills = activatedSkills == null ? List.of() : List.copyOf(activatedSkills);
        patches = patches == null ? List.of() : List.copyOf(patches);
        buildAttempts = buildAttempts == null ? List.of() : List.copyOf(buildAttempts);
        events = events == null ? List.of() : List.copyOf(events);
        agentLoop = agentLoop == null
                ? AgentLoopState.initial(budget == null ? 8 : budget.maxAgentSteps())
                : agentLoop;
        workspaceId = workspaceId == null ? "" : workspaceId;
        approvedOutputPath = approvedOutputPath == null ? "" : approvedOutputPath;
        failureMessage = failureMessage == null ? "" : failureMessage;
    }

    /** 兼容阶段16之前的测试、调用代码和历史构造方式。 */
    public CodingTask(
            String taskId,
            String workflowId,
            WorkflowState workflowSnapshot,
            CodingTaskStage stage,
            String summary,
            AutonomyBudget budget,
            int consumedFiles,
            long consumedBytes,
            int consumedBuildExecutions,
            long consumedDurationMs,
            int repairAttempts,
            List<String> activatedSkills,
            List<PatchFile> patches,
            BuildVerification verification,
            List<BuildAttempt> buildAttempts,
            List<CodingTaskEvent> events,
            String workspaceId,
            String approvedOutputPath,
            String failureMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(taskId, workflowId, workflowSnapshot, stage, summary, budget,
                consumedFiles, consumedBytes, consumedBuildExecutions,
                consumedDurationMs, repairAttempts, activatedSkills, patches,
                verification, buildAttempts, events,
                AgentLoopState.initial(budget == null ? 8 : budget.maxAgentSteps()),
                workspaceId, approvedOutputPath, failureMessage, createdAt, updatedAt);
    }
}
