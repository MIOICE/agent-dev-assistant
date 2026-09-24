package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.cases.a2a.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEvidenceResearchGateway implements EvidenceResearchGateway {

    @Override
    public ResearchResult research(String tenantId, CustomerSystemSnapshot system,
                                   String requirement, RequirementCard card,
                                   String existingRemoteTaskId) {
        EvidenceBundle bundle = new EvidenceBundle("1", tenantId, system.systemId(),
                system.systemVersion(), List.of(), List.of(),
                List.of("知识 A2A 服务尚未启用，不能生成有事实依据的技术方案"), false);
        return new ResearchResult(existingRemoteTaskId, bundle, false);
    }

    @Override
    public void cancel(String tenantId, String remoteTaskId) {
        // No remote task exists when A2A is disabled.
    }
}
