package com.gaozhaoyang.agent.workflow;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearcher;
import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import com.gaozhaoyang.agent.requirement.RequirementAnalyzer;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.SolutionGenerator;
import com.gaozhaoyang.agent.solution.TechnicalSolution;

import java.util.List;

@Service
public class RequirementWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(RequirementWorkflowService.class);

    private final RequirementAnalyzer requirementAnalyzer;
    private final KnowledgeSearcher knowledgeSearcher;
    private final SolutionGenerator solutionGenerator;
    private final WorkflowRepository workflowRepository;

    public RequirementWorkflowService(
            RequirementAnalyzer requirementAnalyzer,
            KnowledgeSearcher knowledgeSearcher,
            SolutionGenerator solutionGenerator,
            WorkflowRepository workflowRepository
    ) {
        this.requirementAnalyzer = requirementAnalyzer;
        this.knowledgeSearcher = knowledgeSearcher;
        this.solutionGenerator = solutionGenerator;
        this.workflowRepository = workflowRepository;
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

    public WorkflowState get(String workflowId) {
        return findRequired(workflowId);
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

        try {
            TechnicalSolution regenerated = solutionGenerator.regenerate(
                    rejectedState.requirementCard(),
                    rejectedState.knowledgeResults(),
                    rejectedState.solutionFeedbacks()
            );
            return workflowRepository.save(
                    rejectedState.completeSolutionGeneration(regenerated)
            );
        } catch (WorkflowPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
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
            RequirementCard card = requirementAnalyzer.analyze(effectiveRequirement);

            // 2. 每个阶段都保存最新快照，便于查询和故障恢复
            currentState = workflowRepository.save(
                    initialState.completeRequirementAnalysis(card)
            );

            // 3. 信息不完整时立即停止，等待用户继续补充
            if (!card.readyForPlanning()) {
                return workflowRepository.save(currentState.waitForClarification());
            }

            // 4. 使用完整需求上下文和业务模块构造检索问题
            String searchQuery = buildKnowledgeQuery(initialState.requirement(), card);

            // 5. 执行向量检索，并把 Spring AI Document 转成稳定的接口返回对象
            List<KnowledgeSearchResult> knowledgeResults = knowledgeSearcher
                    .search(searchQuery)
                    .stream()
                    .map(KnowledgeSearchResult::from)
                    .toList();

            // 6. 检索完成后保存结果，并进入技术方案生成阶段
            currentState = workflowRepository.save(
                    currentState.completeKnowledgeRetrieval(knowledgeResults)
            );

            // 7. 使用结构化需求卡片和真实检索资料生成技术方案
            TechnicalSolution technicalSolution = solutionGenerator.generate(card, knowledgeResults);

            // 8. 方案生成完成后进入人工审批，不能由Agent直接执行高风险操作
            return workflowRepository.save(
                    currentState.completeSolutionGeneration(technicalSolution)
            );
        } catch (WorkflowPersistenceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Workflow {} failed at stage {}",
                    currentState.workflowId(), currentState.stage(), exception);
            return workflowRepository.save(currentState.fail(failureMessage(currentState.stage())));
        }
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

    private String buildKnowledgeQuery(
            String originalRequirement,
            RequirementCard card
    ) {
        String modules = String.join(" ", card.affectedModules());
        return modules.isBlank()
                ? originalRequirement
                : originalRequirement + " " + modules;
    }

}
