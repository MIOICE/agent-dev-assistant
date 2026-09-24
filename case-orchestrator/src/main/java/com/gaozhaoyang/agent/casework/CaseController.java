package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.casework.security.CaseActor;
import com.gaozhaoyang.agent.casework.security.CaseActorContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseWorkflowService service;
    private final CaseActorContext actors;

    public CaseController(CaseWorkflowService service, CaseActorContext actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public CaseProgressView create(@Valid @RequestBody CaseCreateRequest request,
                                   @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                   Authentication authentication) {
        return CaseProgressView.from(service.create(actor(authentication), request, key));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public List<CaseSummary> list(@RequestParam(defaultValue = "20") int limit,
                                  @RequestParam(defaultValue = "0") int offset,
                                  Authentication authentication) {
        return service.list(actor(authentication), limit, offset);
    }

    @GetMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public CaseProgressView progress(@PathVariable String caseId, Authentication authentication) {
        return service.progress(actor(authentication), caseId);
    }

    @PostMapping("/{caseId}/clarifications")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public CaseProgressView clarify(@PathVariable String caseId,
                                    @Valid @RequestBody CaseClarificationRequest request,
                                    Authentication authentication) {
        return CaseProgressView.from(service.clarify(actor(authentication), caseId, request));
    }

    @GetMapping("/{caseId}/technical-proposal")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public TechnicalProposalView technical(@PathVariable String caseId, Authentication authentication) {
        return service.technicalProposal(actor(authentication), caseId);
    }

    @PutMapping("/{caseId}/technical-proposal")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public TechnicalProposalView edit(@PathVariable String caseId,
                                      @Valid @RequestBody SolutionEditRequest request,
                                      Authentication authentication) {
        return TechnicalProposalView.from(service.editSolution(actor(authentication), caseId, request));
    }

    @PostMapping("/{caseId}/reject")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public CaseProgressView reject(@PathVariable String caseId,
                                   @Valid @RequestBody CaseReviewRequest request,
                                   Authentication authentication) {
        return CaseProgressView.from(service.reject(actor(authentication), caseId, request));
    }

    @PostMapping("/{caseId}/approve")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public CaseProgressView approve(@PathVariable String caseId,
                                    @Valid @RequestBody CaseReviewRequest request,
                                    Authentication authentication) {
        return CaseProgressView.from(service.approve(actor(authentication), caseId, request));
    }

    @GetMapping("/{caseId}/business-proposal")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public BusinessProposalView business(@PathVariable String caseId, Authentication authentication) {
        return service.businessProposal(actor(authentication), caseId);
    }

    @PostMapping("/{caseId}/cancel")
    @PreAuthorize("hasAnyRole('IMPLEMENTER','ADMIN')")
    public CaseProgressView cancel(@PathVariable String caseId,
                                   @Valid @RequestBody CaseReviewRequest request,
                                   Authentication authentication) {
        return CaseProgressView.from(service.cancel(actor(authentication), caseId, request));
    }

    private CaseActor actor(Authentication authentication) {
        return actors.requireActor(authentication);
    }
}
