package com.gaozhaoyang.agent.casework.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class CaseActorContext {

    public CaseActor requireActor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("需要登录后访问需求 Case");
        }
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new AccessDeniedException("登录令牌缺少 tenant_id");
        }
        Set<String> roles = new LinkedHashSet<>();
        List<String> claimRoles = jwt.getClaimAsStringList("roles");
        if (claimRoles != null) {
            claimRoles.forEach(role -> roles.add(role.toUpperCase()));
        }
        return new CaseActor(jwt.getSubject(), tenantId, roles);
    }
}
