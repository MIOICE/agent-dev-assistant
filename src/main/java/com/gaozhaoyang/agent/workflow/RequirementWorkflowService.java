package com.gaozhaoyang.agent.workflow;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gaozhaoyang.agent.knowledge.EvidenceResearchReport;
import com.gaozhaoyang.agent.knowledge.EvidenceResearcher;
import com.gaozhaoyang.agent.knowledge.KnowledgeSearcher;
import com.gaozhaoyang.agent.knowledge.SingleQueryEvidenceResearcher;
import com.gaozhaoyang.agent.requirement.RequirementAnalyzer;
import com.gaozhaoyang.agent.requirement.ClarificationQuestion;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.SolutionGenerator;
import com.gaozhaoyang.agent.solution.SolutionCritiqueReport;
import com.gaozhaoyang.agent.solution.SolutionEvidenceCritic;
import com.gaozhaoyang.agent.solution.SolutionGroundingReport;
import com.gaozhaoyang.agent.solution.SolutionGroundingService;
import com.gaozhaoyang.agent.solution.RuleBasedSolutionEvidenceCritic;
import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class RequirementWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(RequirementWorkflowService.class);

    private final RequirementAnalyzer requirementAnalyzer;
    private final EvidenceResearcher evidenceResearcher;
    private final SolutionGenerator solutionGenerator;
    private final SolutionGroundingService solutionGroundingService;
    private final SolutionEvidenceCritic solutionEvidenceCritic;
    private final WorkflowRepository workflowRepository;

    @Autowired
    public RequirementWorkflowService(
            RequirementAnalyzer requirementAnalyzer,
            EvidenceResearcher evidenceResearcher,
            SolutionGenerator solutionGenerator,
            SolutionGroundingService solutionGroundingService,
            SolutionEvidenceCritic solutionEvidenceCritic,
            WorkflowRepository workflowRepository
    ) {
        this.requirementAnalyzer = requirementAnalyzer;
        this.evidenceResearcher = evidenceResearcher;
        this.solutionGenerator = solutionGenerator;
        this.solutionGroundingService = solutionGroundingService;
        this.solutionEvidenceCritic = solutionEvidenceCritic;
        this.workflowRepository = workflowRepository;
    }

    public RequirementWorkflowService(
            RequirementAnalyzer requirementAnalyzer,
            KnowledgeSearcher knowledgeSearcher,
            SolutionGenerator solutionGenerator,
            WorkflowRepository workflowRepository
    ) {
        this(
                requirementAnalyzer,
                new SingleQueryEvidenceResearcher(knowledgeSearcher),
                solutionGenerator,
                new SolutionGroundingService(),
                new RuleBasedSolutionEvidenceCritic(),
                workflowRepository
        );
    }

    public WorkflowState start(String requirement) {
        WorkflowState initialState = WorkflowState.start(requirement);
        workflowRepository.save(initialState);
        return advance(initialState);
    }

    public WorkflowState clarify(
            String workflowId,
            String clarification
    ) {
        WorkflowState currentState = findRequired(workflowId);
        if (currentState.stage() != WorkflowStage.WAITING_CLARIFICATION) {
            throw new InvalidWorkflowStateException(
                    workflowId,
                    currentState.stage(),
                    "补充需求"
            );
        }

        WorkflowState resumedState = currentState.addClarification(clarification);
        workflowRepository.save(resumedState);
        return advance(resumedState);
    }

    public WorkflowState acceptRecommendedClarifications(String workflowId) {
        WorkflowState currentState = findRequired(workflowId);
        requireStage(currentState, WorkflowStage.WAITING_CLARIFICATION, "采用推荐值");
        RequirementCard card = currentState.requirementCard();
        List<ClarificationQuestion> blockingQuestions = card.clarificationQuestions().stream()
                .filter(ClarificationQuestion::blocking)
                .toList();
        if (blockingQuestions.isEmpty()) {
            throw new IllegalArgumentException("当前工作流没有需要确认的阻塞问题");
        }
        if (blockingQuestions.stream().anyMatch(question -> question.recommendedAnswer().isBlank())) {
            throw new IllegalArgumentException("部分阻塞问题没有推荐值，请逐项回答后继续");
        }

        String accepted = "用户已明确接受Agent推荐值：\n" + blockingQuestions.stream()
                .map(question -> question.question() + " => " + question.recommendedAnswer())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        return clarify(workflowId, accepted);
    }

    public WorkflowState get(String workflowId) {
        return findRequired(workflowId);
    }

    public WorkflowTraceReport trace(String workflowId) {
        return WorkflowTraceReport.from(findRequired(workflowId));
    }

    public WorkflowMetrics metrics() {
        List<WorkflowState> states = workflowRepository.findAll(null, 0, 10_000);
        long total = states.size();
        List<WorkflowState> analyzed = states.stream()
                .filter(state -> state.requirementCard() != null)
                .toList();
        long firstPassReady = analyzed.stream()
                .filter(state -> state.events().stream()
                        .noneMatch(event -> event.type() == WorkflowEventType.CLARIFICATION_REQUESTED))
                .count();
        long clarificationWorkflows = states.stream()
                .filter(state -> state.events().stream()
                        .anyMatch(event -> event.type() == WorkflowEventType.CLARIFICATION_REQUESTED))
                .count();
        long recommendationAccepted = states.stream()
                .filter(state -> state.clarifications().stream()
                        .anyMatch(value -> value.contains("用户已明确接受Agent推荐值")))
                .count();
        double averageRounds = states.stream()
                .mapToLong(state -> state.events().stream()
                        .filter(event -> event.type() == WorkflowEventType.CLARIFICATION_RECEIVED)
                        .count())
                .average()
                .orElse(0);
        double averageDuration = states.stream()
                .mapToLong(state -> Duration.between(state.createdAt(), state.updatedAt()).toMillis())
                .average()
                .orElse(0);
        double averageOperationDuration = states.stream()
                .flatMap(state -> state.traceSpans().stream())
                .mapToLong(AgentTraceSpan::durationMs)
                .average()
                .orElse(0);

        return new WorkflowMetrics(
                total,
                analyzed.size(),
                ratio(firstPassReady, analyzed.size()),
                averageRounds,
                ratio(recommendationAccepted, clarificationWorkflows),
                ratio(states.stream().filter(state -> state.stage() == WorkflowStage.COMPLETED).count(), total),
                ratio(states.stream().filter(state -> state.stage() == WorkflowStage.FAILED).count(), total),
                averageDuration,
                averageOperationDuration,
                Instant.now()
        );
    }

    public WorkflowPage list(WorkflowStage stage, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = workflowRepository.count(stage);
        List<WorkflowSummary> content = workflowRepository
                .findAll(stage, safePage * safeSize, safeSize)
                .stream()
                .map(WorkflowSummary::from)
                .toList();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new WorkflowPage(content, safePage, safeSize, total, totalPages);
    }

    public WorkflowState approve(String workflowId, String comment) {
        WorkflowState currentState = findRequired(workflowId);
        requireStage(currentState, WorkflowStage.WAITING_APPROVAL, "审批通过");
        return workflowRepository.save(currentState.approve(comment));
    }

    public WorkflowState reject(String workflowId, String feedback) {
        WorkflowState currentState = findRequired(workflowId);
        requireStage(currentState, WorkflowStage.WAITING_APPROVAL, "驳回方案");
        WorkflowState rejectedState = workflowRepository.save(currentState.reject(feedback));

        String activeOperation = "agent.solution-regeneration";
        Instant activeOperationStartedAt = Instant.now();
        try {
            TechnicalSolution regenerated = solutionGenerator.regenerate(
                    rejectedState.requirementCard(),
                    rejectedState.knowledgeResults(),
                    rejectedState.solutionFeedbacks()
            );
            rejectedState = rejectedState.addTraceSpan(AgentTraceSpan.success(
                    rejectedState.workflowId(),
                    activeOperation,
                    activeOperationStartedAt,
                    Map.of("feedback.count", String.valueOf(rejectedState.solutionFeedbacks().size()))
            ));
            activeOperation = "agent.solution-grounding";
            activeOperationStartedAt = Instant.now();
            SolutionGroundingReport grounding = solutionGroundingService.ground(
                    regenerated,
                    rejectedState.evidenceResearch()
            );
            rejectedState = rejectedState.addTraceSpan(AgentTraceSpan.success(
                    rejectedState.workflowId(),
                    activeOperation,
                    activeOperationStartedAt,
                    groundingAttributes(grounding)
            ));
            activeOperation = "agent.claim-evidence-critic";
            activeOperationStartedAt = Instant.now();
            SolutionCritiqueReport critique = solutionEvidenceCritic.critique(
                    grounding,
                    rejectedState.knowledgeResults()
            );
            rejectedState = rejectedState.addTraceSpan(AgentTraceSpan.success(
                    rejectedState.workflowId(),
                    activeOperation,
                    activeOperationStartedAt,
                    critiqueAttributes(critique)
            ));
            return workflowRepository.save(
                    rejectedState.completeSolutionGeneration(regenerated, grounding, critique)
            );
        } catch (WorkflowPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            rejectedState = rejectedState.addTraceSpan(AgentTraceSpan.error(
                    rejectedState.workflowId(),
                    activeOperation,
                    activeOperationStartedAt,
                    exception
            ));
            log.error("Regenerating solution failed for workflow {} at stage {}",
                    rejectedState.workflowId(), rejectedState.stage(), exception);
            return workflowRepository.save(rejectedState.fail(failureMessage(rejectedState.stage())));
        }
    }

    public WorkflowState retry(String workflowId) {
        WorkflowState currentState = findRequired(workflowId);
        requireStage(currentState, WorkflowStage.FAILED, "重试工作流");
        WorkflowState retryingState = workflowRepository.save(currentState.retry());
        return advance(retryingState);
    }

    private WorkflowState advance(WorkflowState initialState) {
        WorkflowState currentState = initialState;
        try {
            String effectiveRequirement = initialState.effectiveRequirement();

            // 1. 分析原始需求和历次补充信息组成的完整上下文
            Instant analysisStartedAt = Instant.now();
            RequirementCard card;
            try {
                card = requirementAnalyzer.analyze(effectiveRequirement);
                currentState = currentState.addTraceSpan(AgentTraceSpan.success(
                        currentState.workflowId(),
                        "agent.requirement-analysis",
                        analysisStartedAt,
                        Map.of(
                                "input.chars", String.valueOf(effectiveRequirement.length()),
                                "output.blockingQuestions", String.valueOf(card.missingInformation().size()),
                                "output.agentDefaults", String.valueOf(card.clarificationQuestions().stream()
                                        .filter(question -> !question.blocking()).count()),
                                "output.explainedQuestions", String.valueOf(card.clarificationQuestions().stream()
                                        .filter(question -> !question.reason().isBlank()
                                                && !question.impact().isBlank()).count()),
                                "output.ready", String.valueOf(card.readyForPlanning())
                        )
                ));
            } catch (RuntimeException exception) {
                currentState = currentState.addTraceSpan(AgentTraceSpan.error(
                        currentState.workflowId(),
                        "agent.requirement-analysis",
                        analysisStartedAt,
                        exception
                ));
                throw exception;
            }

            // 2. 每个阶段都保存最新快照，便于查询和故障恢复
            currentState = workflowRepository.save(
                    currentState.completeRequirementAnalysis(card)
            );

            // 3. 信息不完整时立即停止，等待用户继续补充
            if (!card.readyForPlanning()) {
                return workflowRepository.save(currentState.waitForClarification());
            }

            // 4. 先规划证据需求，再在确定性预算内执行多轮检索和充分性检查
            Instant retrievalStartedAt = Instant.now();
            EvidenceResearchReport evidenceReport;
            try {
                evidenceReport = evidenceResearcher.research(
                        initialState.requirement(),
                        effectiveRequirement,
                        card
                );
                currentState = currentState.addTraceSpan(AgentTraceSpan.success(
                        currentState.workflowId(),
                        "rag.agentic-evidence-research",
                        retrievalStartedAt,
                        Map.of(
                                "planning.mode", evidenceReport.planningMode(),
                                "rounds.used", String.valueOf(evidenceReport.roundsUsed()),
                                "queries.used", String.valueOf(evidenceReport.queriesUsed()),
                                "evidence.count", String.valueOf(evidenceReport.evidence().size()),
                                "evidence.sufficient", String.valueOf(evidenceReport.sufficient()),
                                "unresolved.count", String.valueOf(evidenceReport.unresolvedNeedIds().size())
                        )
                ));
            } catch (RuntimeException exception) {
                currentState = currentState.addTraceSpan(AgentTraceSpan.error(
                        currentState.workflowId(),
                        "rag.agentic-evidence-research",
                        retrievalStartedAt,
                        exception
                ));
                throw exception;
            }

            // 5. 检索完成后保存计划、轨迹、证据和未解决缺口
            currentState = workflowRepository.save(
                    currentState.completeKnowledgeRetrieval(evidenceReport)
            );

            // 6. 使用结构化需求卡片和真实检索资料生成技术方案
            Instant solutionStartedAt = Instant.now();
            TechnicalSolution technicalSolution;
            try {
                technicalSolution = solutionGenerator.generate(
                        card,
                        evidenceReport.evidence()
                );
                currentState = currentState.addTraceSpan(AgentTraceSpan.success(
                        currentState.workflowId(),
                        "agent.solution-generation",
                        solutionStartedAt,
                        Map.of(
                                "knowledge.count", String.valueOf(evidenceReport.evidence().size()),
                                "evidence.sufficient", String.valueOf(evidenceReport.sufficient()),
                                "feedback.count", String.valueOf(currentState.solutionFeedbacks().size())
                        )
                ));
            } catch (RuntimeException exception) {
                currentState = currentState.addTraceSpan(AgentTraceSpan.error(
                        currentState.workflowId(),
                        "agent.solution-generation",
                        solutionStartedAt,
                        exception
                ));
                throw exception;
            }

            // 7. 为每条方案结论绑定检索证据，未绑定内容必须显式暴露
            Instant groundingStartedAt = Instant.now();
            SolutionGroundingReport grounding;
            try {
                grounding = solutionGroundingService.ground(
                        technicalSolution,
                        evidenceReport
                );
                currentState = currentState.addTraceSpan(AgentTraceSpan.success(
                        currentState.workflowId(),
                        "agent.solution-grounding",
                        groundingStartedAt,
                        groundingAttributes(grounding)
                ));
            } catch (RuntimeException exception) {
                currentState = currentState.addTraceSpan(AgentTraceSpan.error(
                        currentState.workflowId(),
                        "agent.solution-grounding",
                        groundingStartedAt,
                        exception
                ));
                throw exception;
            }

            // 8. 批量检查每条结论是否被证据支持；Java再次校验claimId与证据ID
            Instant critiqueStartedAt = Instant.now();
            SolutionCritiqueReport critique;
            try {
                critique = solutionEvidenceCritic.critique(
                        grounding,
                        evidenceReport.evidence()
                );
                currentState = currentState.addTraceSpan(AgentTraceSpan.success(
                        currentState.workflowId(),
                        "agent.claim-evidence-critic",
                        critiqueStartedAt,
                        critiqueAttributes(critique)
                ));
            } catch (RuntimeException exception) {
                currentState = currentState.addTraceSpan(AgentTraceSpan.error(
                        currentState.workflowId(),
                        "agent.claim-evidence-critic",
                        critiqueStartedAt,
                        exception
                ));
                throw exception;
            }

            // 9. 审查信号随快照持久化，再进入人工审批；Agent不直接执行高风险操作
            return workflowRepository.save(
                    currentState.completeSolutionGeneration(technicalSolution, grounding, critique)
            );
        } catch (WorkflowPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Workflow {} failed at stage {}",
                    currentState.workflowId(), currentState.stage(), exception);
            return workflowRepository.save(currentState.fail(failureMessage(currentState.stage())));
        }
    }

    private Map<String, String> groundingAttributes(SolutionGroundingReport grounding) {
        return Map.of(
                "claims.total", String.valueOf(grounding.totalFactualClaims()),
                "claims.linked", String.valueOf(grounding.evidenceLinkedClaims()),
                "claims.unsupported", String.valueOf(grounding.unsupportedClaims()),
                "claims.assumptions", String.valueOf(grounding.assumptionClaims()),
                "grounding.rate", String.valueOf(grounding.groundingRate()),
                "evidence.sufficient", String.valueOf(grounding.evidenceSufficient())
        );
    }

    private Map<String, String> critiqueAttributes(SolutionCritiqueReport critique) {
        return Map.of(
                "critic.mode", critique.mode(),
                "claims.supported", String.valueOf(critique.supportedClaims()),
                "claims.contradicted", String.valueOf(critique.contradictedClaims()),
                "claims.insufficient", String.valueOf(critique.insufficientClaims()),
                "claims.notEvaluated", String.valueOf(critique.notEvaluatedClaims()),
                "support.rate", String.valueOf(critique.supportRate()),
                "safeForApproval", String.valueOf(critique.safeForApproval())
        );
    }

    private WorkflowState findRequired(String workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }

    private void requireStage(
            WorkflowState state,
            WorkflowStage expectedStage,
            String operation
    ) {
        if (state.stage() != expectedStage) {
            throw new InvalidWorkflowStateException(
                    state.workflowId(),
                    state.stage(),
                    operation
            );
        }
    }

    private String failureMessage(WorkflowStage stage) {
        return "工作流在“" + stageLabel(stage) + "”阶段执行失败，请检查服务日志或配置后重试";
    }

    private String stageLabel(WorkflowStage stage) {
        return switch (stage) {
            case REQUIREMENT_ANALYSIS -> "需求分析";
            case KNOWLEDGE_RETRIEVAL -> "知识检索";
            case SOLUTION_GENERATION -> "方案生成";
            default -> "任务执行";
        };
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

}
