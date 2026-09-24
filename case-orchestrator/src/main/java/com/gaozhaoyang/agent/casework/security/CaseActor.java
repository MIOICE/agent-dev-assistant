package com.gaozhaoyang.agent.casework.security;

import java.util.Set;

public record CaseActor(String actorId, String tenantId, Set<String> roles) {

    public CaseActor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
