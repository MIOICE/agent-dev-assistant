package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.casework.security.CaseActor;
import com.gaozhaoyang.agent.solution.TechnicalSolution;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CaseWorkflowService {

    private final CaseRepository repository;
    private final CaseWorkDispatcher dispatcher;
    private final EvidenceResearchGateway evidenceGateway;

    public CaseWorkflowService(CaseRepository repository, CaseWorkDispatcher dispatcher,
                               EvidenceResearchGateway evidenceGateway) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.evidenceGateway = evidenceGateway;
    }

    public RequirementCase create(CaseActor actor, CaseCreateRequest request, String idempotencyKey) {
        Instant now = Instant.now();
        RequirementCase created = RequirementCase.create(UUID.randomUUID().toString(),
                actor.tenantId(), actor.actorId(), request.requirement().trim(), request.system(), now);
        RequirementCase stored = repository.insert(created, idempotencyKey);
        if (stored.caseId().equals(created.caseId())) {
            repository.audit(stored.caseId(), actor.tenantId(), actor.actorId(), primaryRole(actor),
                    "CASE_CREATED", Map.of("system", request.system()));
            dispatcher.submit(stored.caseId());
        }
        return stored;
    }

    public List<CaseSummary> list(CaseActor actor, int limit, int offset) {
        return repository.findByTenant(actor.tenantId(), Math.min(Math.max(limit, 1), 100),
                        Math.max(offset, 0))
                .stream().map(CaseSummary::from).toList();
    }

    public CaseProgressView progress(CaseActor actor, String caseId) {
        return CaseProgressView.from(require(actor, caseId));
    }

    public RequirementCase clarify(CaseActor actor, String caseId,
                                   CaseClarificationRequest request) {
        RequirementCase item = require(actor, caseId);
        if (item.stage() != CaseStage.WAITING_FOR_CLARIFICATION) {
            throw new InvalidCaseStateException("当前 Case 不处于等待澄清状态");
        }
        RequirementCase updated = repository.update(item.withClarifications(request.answers(), Instant.now()));
        repository.audit(caseId, actor.tenantId(), actor.actorId(), primaryRole(actor),
                "CLARIFICATION_SUBMITTED", Map.of("questionCount", request.answers().size()));
        dispatcher.submit(caseId);
        return updated;
    }

    public TechnicalProposalView technicalProposal(CaseActor actor, String caseId) {
        requireImplementer(actor);
        return TechnicalProposalView.from(require(actor, caseId));
    }

    public RequirementCase editSolution(CaseActor actor, String caseId, SolutionEditRequest request) {
        requireImplementer(actor);
        RequirementCase item = require(actor, caseId);
        if (item.stage() != CaseStage.WAITING_FOR_IMPLEMENTER_REVIEW
                && item.stage() != CaseStage.REVISION_REQUESTED) {
            throw new InvalidCaseStateException("只有待实施评审的方案可以修改");
        }
        List<SolutionRevision> revisions = new ArrayList<>(item.revisions());
        revisions.add(new SolutionRevision(revisions.size() + 1, request.solution(),
                actor.actorId(), request.changeReason(), Instant.now()));
        RequirementCase updated = repository.update(item.progress(
                CaseStage.WAITING_FOR_IMPLEMENTER_REVIEW, item.requirementCard(),
                item.remoteTaskId(), item.evidenceBundle(), request.solution(), revisions,
                null, null, null, Instant.now()));
        repository.audit(caseId, actor.tenantId(), actor.actorId(), primaryRole(actor),
                "SOLUTION_EDITED", Map.of("revision", revisions.size(),
                        "reason", request.changeReason()));
        return updated;
    }

    public RequirementCase reject(CaseActor actor, String caseId, CaseReviewRequest request) {
        requireImplementer(actor);
        RequirementCase item = require(actor, caseId);
        if (item.stage() != CaseStage.WAITING_FOR_IMPLEMENTER_REVIEW) {
            throw new InvalidCaseStateException("当前方案不处于待评审状态");
        }
        RequirementCase updated = repository.update(item.progress(CaseStage.REVISION_REQUESTED,
                item.requirementCard(), item.remoteTaskId(), item.evidenceBundle(),
                item.currentSolution(), item.revisions(), null, null, request.comment(), Instant.now()));
        repository.audit(caseId, actor.tenantId(), actor.actorId(), primaryRole(actor),
                "SOLUTION_REJECTED", Map.of("comment", request.comment()));
        return updated;
    }

    public RequirementCase approve(CaseActor actor, String caseId, CaseReviewRequest request) {
        requireImplementer(actor);
        RequirementCase item = require(actor, caseId);
        if (item.stage() != CaseStage.WAITING_FOR_IMPLEMENTER_REVIEW) {
            throw new InvalidCaseStateException("当前方案不处于待评审状态");
        }
        if (item.evidenceBundle() == null || !item.evidenceBundle().sufficient()) {
            throw new InvalidCaseStateException("证据不足的方案不能生成为客户沟通稿");
        }
        CaseApproval approval = new CaseApproval(actor.actorId(), request.comment(), Instant.now());
        RequirementCase updated = repository.update(item.progress(CaseStage.PUBLISHED,
                item.requirementCard(), item.remoteTaskId(), item.evidenceBundle(),
                item.currentSolution(), item.revisions(), approval, null, null, Instant.now()));
        repository.audit(caseId, actor.tenantId(), actor.actorId(), primaryRole(actor),
                "SOLUTION_APPROVED_FOR_CUSTOMER_COMMUNICATION", Map.of("comment", request.comment()));
        return updated;
    }

    public BusinessProposalView businessProposal(CaseActor actor, String caseId) {
        RequirementCase item = require(actor, caseId);
        if (item.stage() != CaseStage.PUBLISHED) {
            throw new InvalidCaseStateException("方案尚未由实施人员确认，不能生成客户沟通稿");
        }
        return BusinessProposalView.from(item);
    }

    public RequirementCase cancel(CaseActor actor, String caseId, CaseReviewRequest request) {
        requireImplementer(actor);
        RequirementCase item = require(actor, caseId);
        if (item.stage() == CaseStage.PUBLISHED || item.stage() == CaseStage.CANCELED) {
            throw new InvalidCaseStateException("已批准或已取消的 Case 不能再次取消");
        }
        RequirementCase canceled = repository.update(item.progress(CaseStage.CANCELED,
                item.requirementCard(), item.remoteTaskId(), item.evidenceBundle(),
                item.currentSolution(), item.revisions(), item.approval(),
                "CANCELED_BY_IMPLEMENTER", request.comment(), Instant.now()));
        String remoteCancel = "NOT_REQUIRED";
        if (item.remoteTaskId() != null && !item.remoteTaskId().isBlank()) {
            try {
                evidenceGateway.cancel(actor.tenantId(), item.remoteTaskId());
                remoteCancel = "REQUESTED";
            } catch (RuntimeException exception) {
                // Knowledge research is read-only. Local cancellation remains the
                // authority boundary even when a best-effort remote cancel fails.
                remoteCancel = "FAILED";
            }
        }
        repository.audit(caseId, actor.tenantId(), actor.actorId(), primaryRole(actor),
                "CASE_CANCELED", Map.of("comment", request.comment(),
                        "remoteCancel", remoteCancel));
        return canceled;
    }

    private RequirementCase require(CaseActor actor, String caseId) {
        return repository.findByTenantAndId(actor.tenantId(), caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
    }

    private void requireImplementer(CaseActor actor) {
        if (!actor.hasRole("IMPLEMENTER") && !actor.hasRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("仅实施人员可执行该操作");
        }
    }

    private String primaryRole(CaseActor actor) {
        return actor.roles().stream().findFirst().orElse("UNKNOWN");
    }
}
