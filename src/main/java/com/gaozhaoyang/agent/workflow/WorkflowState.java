package com.gaozhaoyang.agent.workflow;

import com.gaozhaoyang.agent.knowledge.EvidenceResearchReport;
import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.SolutionGroundingReport;
import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record WorkflowState(
        String workflowId,
        String requirement,
        List<String> clarifications,
        WorkflowStage stage,
        RequirementCard requirementCard,
        List<KnowledgeSearchResult> knowledgeResults,
        EvidenceResearchReport evidenceResearch,
        TechnicalSolution technicalSolution,
        SolutionGroundingReport solutionGrounding,
        List<String> solutionFeedbacks,
        List<WorkflowEvent> events,
        int revision,
        Instant createdAt,
        Instant updatedAt,
        List<AgentTraceSpan> traceSpans,
        String failureMessage
) {
    public WorkflowState {
        clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
        knowledgeResults = knowledgeResults == null ? List.of() : List.copyOf(knowledgeResults);
        evidenceResearch = evidenceResearch == null
                ? EvidenceResearchReport.empty()
                : evidenceResearch;
        solutionGrounding = solutionGrounding == null
                ? SolutionGroundingReport.empty()
                : solutionGrounding;
        solutionFeedbacks = solutionFeedbacks == null ? List.of() : List.copyOf(solutionFeedbacks);
        events = events == null ? List.of() : List.copyOf(events);
        traceSpans = traceSpans == null ? List.of() : List.copyOf(traceSpans);
        stage = stage == null ? WorkflowStage.REQUIREMENT_ANALYSIS : stage;
        revision = Math.max(revision, 1);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static WorkflowState start(String requirement) {
        Instant now = Instant.now();
        return new WorkflowState(
                UUID.randomUUID().toString(),
                requirement.trim(),
                List.of(),
                WorkflowStage.REQUIREMENT_ANALYSIS,
                null,
                List.of(),
                EvidenceResearchReport.empty(),
                null,
                SolutionGroundingReport.empty(),
                List.of(),
                List.of(new WorkflowEvent(
                        UUID.randomUUID().toString(),
                        WorkflowEventType.CREATED,
                        "工作流已创建，开始分析需求",
                        now
                )),
                1,
                now,
                now,
                List.of(),
                null
        );
    }

    public WorkflowState completeRequirementAnalysis(RequirementCard card) {
        return transition(WorkflowStage.KNOWLEDGE_RETRIEVAL, card, knowledgeResults, evidenceResearch,
                technicalSolution, solutionGrounding, clarifications, solutionFeedbacks, null,
                WorkflowEventType.REQUIREMENT_ANALYZED, "需求已转换为结构化需求卡片");
    }

    public WorkflowState waitForClarification() {
        return transition(WorkflowStage.WAITING_CLARIFICATION, requirementCard,
                knowledgeResults, evidenceResearch, technicalSolution, solutionGrounding,
                clarifications, solutionFeedbacks, null,
                WorkflowEventType.CLARIFICATION_REQUESTED, "需求信息不足，等待用户补充");
    }

    public WorkflowState completeKnowledgeRetrieval(EvidenceResearchReport report) {
        EvidenceResearchReport safeReport = report == null
                ? EvidenceResearchReport.empty()
                : report;
        List<KnowledgeSearchResult> safeResults = safeReport.evidence();
        return transition(WorkflowStage.SOLUTION_GENERATION, requirementCard, safeResults,
                safeReport,
                technicalSolution, solutionGrounding, clarifications, solutionFeedbacks, null,
                WorkflowEventType.KNOWLEDGE_RETRIEVED,
                "证据研究完成：规划 " + safeReport.plannedNeeds().size()
                        + " 项、执行 " + safeReport.queriesUsed()
                        + " 次检索、保留 " + safeResults.size() + " 条证据");
    }

    public WorkflowState completeSolutionGeneration(
            TechnicalSolution solution,
            SolutionGroundingReport grounding
    ) {
        return transition(WorkflowStage.WAITING_APPROVAL, requirementCard, knowledgeResults, evidenceResearch,
                solution, grounding, clarifications, solutionFeedbacks, null,
                WorkflowEventType.SOLUTION_GENERATED,
                solutionFeedbacks.isEmpty() ? "技术方案已生成，等待人工审批" : "技术方案已根据审批意见重新生成");
    }

    public WorkflowState addClarification(String clarification) {
        List<String> updated = new ArrayList<>(clarifications);
        updated.add(clarification.trim());
        return transition(WorkflowStage.REQUIREMENT_ANALYSIS, null, List.of(), EvidenceResearchReport.empty(), null,
                SolutionGroundingReport.empty(), updated, solutionFeedbacks, null,
                WorkflowEventType.CLARIFICATION_RECEIVED, "用户已补充需求信息，重新开始分析");
    }

    public WorkflowState approve(String comment) {
        String normalized = comment == null ? "" : comment.trim();
        return transition(WorkflowStage.COMPLETED, requirementCard, knowledgeResults, evidenceResearch,
                technicalSolution, solutionGrounding, clarifications, solutionFeedbacks, null,
                WorkflowEventType.APPROVED,
                normalized.isBlank() ? "技术方案已通过人工审批" : "审批通过：" + normalized);
    }

    public WorkflowState reject(String feedback) {
        List<String> updated = new ArrayList<>(solutionFeedbacks);
        updated.add(feedback.trim());
        return transition(WorkflowStage.SOLUTION_GENERATION, requirementCard, knowledgeResults, evidenceResearch,
                technicalSolution, solutionGrounding, clarifications, updated, null,
                WorkflowEventType.REJECTED, "审批驳回，重新生成方案：" + feedback.trim());
    }

    public WorkflowState fail(String message) {
        String safeMessage = message == null || message.isBlank() ? "工作流执行失败" : message;
        return transition(WorkflowStage.FAILED, requirementCard, knowledgeResults, evidenceResearch,
                technicalSolution, solutionGrounding, clarifications, solutionFeedbacks, safeMessage,
                WorkflowEventType.FAILED, safeMessage);
    }

    public WorkflowState retry() {
        return transition(WorkflowStage.REQUIREMENT_ANALYSIS, null, List.of(), EvidenceResearchReport.empty(), null,
                SolutionGroundingReport.empty(), clarifications, solutionFeedbacks, null,
                WorkflowEventType.RETRIED, "用户发起重试，重新执行工作流");
    }

    public String effectiveRequirement() {
        if (clarifications.isEmpty()) {
            return requirement;
        }
        return requirement + "\n\n用户补充信息：\n- " + String.join("\n- ", clarifications);
    }

    public WorkflowState addTraceSpan(AgentTraceSpan span) {
        List<AgentTraceSpan> updatedSpans = new ArrayList<>(traceSpans);
        updatedSpans.add(span);
        return new WorkflowState(
                workflowId, requirement, clarifications, stage, requirementCard,
                knowledgeResults, evidenceResearch, technicalSolution, solutionGrounding,
                solutionFeedbacks, events,
                revision, createdAt, updatedAt, updatedSpans, failureMessage
        );
    }

    private WorkflowState transition(
            WorkflowStage nextStage,
            RequirementCard nextCard,
            List<KnowledgeSearchResult> nextKnowledge,
            EvidenceResearchReport nextEvidenceResearch,
            TechnicalSolution nextSolution,
            SolutionGroundingReport nextGrounding,
            List<String> nextClarifications,
            List<String> nextFeedbacks,
            String nextFailureMessage,
            WorkflowEventType eventType,
            String eventMessage
    ) {
        List<WorkflowEvent> updatedEvents = new ArrayList<>(events);
        updatedEvents.add(WorkflowEvent.of(eventType, eventMessage));
        return new WorkflowState(workflowId, requirement, nextClarifications, nextStage,
                nextCard, nextKnowledge, nextEvidenceResearch, nextSolution, nextGrounding,
                nextFeedbacks, updatedEvents,
                revision + 1, createdAt, Instant.now(), traceSpans, nextFailureMessage);
    }
}
