package com.gaozhaoyang.agent.casework;

import java.util.List;
import java.util.Optional;

public interface CaseRepository {
    RequirementCase insert(RequirementCase requirementCase, String idempotencyKey);

    RequirementCase update(RequirementCase requirementCase);

    Optional<RequirementCase> findById(String caseId);

    Optional<RequirementCase> findByTenantAndId(String tenantId, String caseId);

    Optional<RequirementCase> findByTenantAndIdempotencyKey(String tenantId, String idempotencyKey);

    List<RequirementCase> findByTenant(String tenantId, int limit, int offset);

    List<RequirementCase> findRecoverable(int limit);

    void audit(String caseId, String tenantId, String actorId, String actorRole,
               String action, Object details);
}
