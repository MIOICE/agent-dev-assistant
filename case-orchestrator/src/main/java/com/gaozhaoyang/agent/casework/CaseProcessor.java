package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.casework.skill.CaseSkillCatalog;
import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import com.gaozhaoyang.agent.requirement.RequirementAnalyzer;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.SolutionGenerator;
import com.gaozhaoyang.agent.solution.TechnicalSolution;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CaseProcessor {

    private final CaseRepository repository;
    private final RequirementAnalyzer analyzer;
    private final EvidenceResearchGateway evidenceGateway;
    private final EvidencePolicyService evidencePolicy;
    private final SolutionGenerator solutionGenerator;
    private final CaseSkillCatalog skillCatalog;
    private final String solutionPromptVersion;

    public CaseProcessor(CaseRepository repository, RequirementAnalyzer analyzer,
                         EvidenceResearchGateway evidenceGateway,
                         EvidencePolicyService evidencePolicy,
                         SolutionGenerator solutionGenerator,
                         CaseSkillCatalog skillCatalog,
                         @Value("${app.cases.prompts.solution-version}") String solutionPromptVersion) {
        this.repository = repository;
        this.analyzer = analyzer;
        this.evidenceGateway = evidenceGateway;
        this.evidencePolicy = evidencePolicy;
        this.solutionGenerator = solutionGenerator;
        this.skillCatalog = skillCatalog;
        this.solutionPromptVersion = solutionPromptVersion;
    }

    public void process(String caseId) {
        try {
            RequirementCase item = repository.findById(caseId)
                    .orElseThrow(() -> new CaseNotFoundException(caseId));
            if (item.stage() == CaseStage.ANALYZING_REQUIREMENT) {
                item = analyze(item);
            }
            if (item.stage() == CaseStage.RESEARCHING_EVIDENCE) {
                item = research(item);
            }
            if (item.stage() == CaseStage.GENERATING_SOLUTION) {
                generate(item);
            }
        } catch (OptimisticLockingFailureException ignored) {
            // A duplicate resume tick lost the optimistic-lock race; the winner continues.
        } catch (Exception exception) {
            repository.findById(caseId).ifPresent(current -> {
                if (isTerminalOrHumanGate(current.stage())) {
                    return;
                }
                RequirementCase failed = current.progress(CaseStage.FAILED,
                        current.requirementCard(), current.remoteTaskId(), current.evidenceBundle(),
                        current.currentSolution(), current.revisions(), current.approval(),
                        "CASE_PROCESSING_FAILED", safeMessage(exception), Instant.now());
                try {
                    repository.update(failed);
                } catch (OptimisticLockingFailureException ignored) {
                    // Another worker already advanced the case.
                }
            });
        }
    }

    private RequirementCase analyze(RequirementCase item) {
        RequirementCard card = analyzer.analyze(requirementWithAnswers(item));
        CaseStage next = card.readyForPlanning()
                ? CaseStage.RESEARCHING_EVIDENCE : CaseStage.WAITING_FOR_CLARIFICATION;
        return repository.update(item.progress(next, card, null, null, null,
                item.revisions(), null, null, null, Instant.now()));
    }

    private RequirementCase research(RequirementCase item) {
        EvidenceResearchGateway.ResearchResult result = evidenceGateway.research(
                item.tenantId(), item.systemSnapshot(), item.requirement(),
                item.requirementCard(), item.remoteTaskId());
        if (result.pending()) {
            return repository.update(item.progress(CaseStage.RESEARCHING_EVIDENCE,
                    item.requirementCard(), result.remoteTaskId(), null, null,
                    item.revisions(), null, null, null, Instant.now()));
        }
        EvidenceBundle governed = evidencePolicy.enforce(result.bundle(), item.tenantId(),
                item.systemSnapshot());
        CaseStage next = governed.sufficient()
                ? CaseStage.GENERATING_SOLUTION : CaseStage.EVIDENCE_INSUFFICIENT;
        return repository.update(item.progress(next, item.requirementCard(),
                result.remoteTaskId(), governed, null, item.revisions(), null,
                null, null, Instant.now()));
    }

    private RequirementCase generate(RequirementCase item) {
        TechnicalSolution solution = solutionGenerator.generate(item.requirementCard(),
                toKnowledgeResults(item.evidenceBundle()));
        List<SolutionRevision> revisions = new ArrayList<>(item.revisions());
        revisions.add(new SolutionRevision(revisions.size() + 1, solution,
                "case-orchestrator", "基于受控证据生成方案初稿", Instant.now(),
                solutionPromptVersion, skillCatalog.manifestHash()));
        return repository.update(item.progress(CaseStage.WAITING_FOR_IMPLEMENTER_REVIEW,
                item.requirementCard(), item.remoteTaskId(), item.evidenceBundle(), solution,
                revisions, null, null, null, Instant.now()));
    }

    private List<KnowledgeSearchResult> toKnowledgeResults(EvidenceBundle bundle) {
        if (bundle == null) {
            return List.of();
        }
        return bundle.evidence().stream().map(item -> new KnowledgeSearchResult(
                item.chunkId(), item.documentId(), item.title(),
                item.chunkIndex() == null ? 0 : item.chunkIndex(), List.of(),
                "A2A", item.source(), "", "", "", "", true,
                item.content(), item.score() == null ? 0.0 : item.score(),
                item.score() == null ? 0.0 : item.score(), 0.0
        )).toList();
    }

    private String requirementWithAnswers(RequirementCase item) {
        if (item.clarificationAnswers().isEmpty()) {
            return item.requirement();
        }
        StringBuilder content = new StringBuilder(item.requirement())
                .append("\n实施人员已与客户确认并记录如下：\n");
        item.clarificationAnswers().forEach((question, answer) ->
                content.append("问题：").append(question).append("；回答：")
                        .append(answer).append("\n"));
        return content.toString();
    }

    private boolean isTerminalOrHumanGate(CaseStage stage) {
        return switch (stage) {
            case WAITING_FOR_CLARIFICATION, EVIDENCE_INSUFFICIENT,
                    WAITING_FOR_IMPLEMENTER_REVIEW, PUBLISHED, CANCELED -> true;
            default -> false;
        };
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "后台处理失败，请实施人员查看链路日志" : message.substring(0, Math.min(300, message.length()));
    }
}
