package com.gaozhaoyang.agent.casework;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EvidencePolicyService {

    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous", "ignore all previous", "system prompt", "developer message",
            "忽略之前", "忽略以上", "系统提示词", "开发者消息", "执行以下命令"
    );

    public EvidenceBundle enforce(EvidenceBundle bundle, String tenantId,
                                  CustomerSystemSnapshot system) {
        if (!tenantId.equals(bundle.tenantId())
                || !system.systemId().equals(bundle.systemId())
                || !system.systemVersion().equals(bundle.systemVersion())) {
            return rejected(bundle, "证据包的客户或系统版本与 Case 快照不一致");
        }
        List<EvidenceBundle.EvidenceItem> safeEvidence = bundle.evidence().stream()
                .filter(item -> system.systemVersion().equals(item.systemVersion()))
                .filter(item -> !containsPromptInjection(item.content()))
                .toList();
        List<String> unresolved = new ArrayList<>(bundle.unresolved());
        int rejectedCount = bundle.evidence().size() - safeEvidence.size();
        if (rejectedCount > 0) {
            unresolved.add("有 " + rejectedCount + " 条证据因版本不符或包含提示注入特征而被隔离");
        }
        boolean sufficient = bundle.sufficient() && unresolved.isEmpty() && !safeEvidence.isEmpty();
        return new EvidenceBundle(bundle.schemaVersion(), tenantId, system.systemId(),
                system.systemVersion(), safeEvidence, bundle.coverage(), unresolved, sufficient);
    }

    private EvidenceBundle rejected(EvidenceBundle bundle, String reason) {
        List<String> unresolved = new ArrayList<>(bundle.unresolved());
        unresolved.add(reason);
        return new EvidenceBundle(bundle.schemaVersion(), bundle.tenantId(), bundle.systemId(),
                bundle.systemVersion(), List.of(), bundle.coverage(), unresolved, false);
    }

    private boolean containsPromptInjection(String content) {
        if (content == null) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return INJECTION_MARKERS.stream().anyMatch(normalized::contains);
    }
}
