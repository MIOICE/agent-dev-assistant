package com.gaozhaoyang.agent.solution;

import java.util.List;
import java.util.Objects;

public record ClaimEvidenceEvaluationCase(
        String id,
        String claim,
        String evidence,
        ClaimEvidenceVerdict expectedVerdict,
        List<String> tags
) {
    public ClaimEvidenceEvaluationCase {
        Objects.requireNonNull(id, "评测样例 ID 不能为空");
        Objects.requireNonNull(claim, "评测结论不能为空");
        Objects.requireNonNull(evidence, "评测证据不能为空");
        Objects.requireNonNull(expectedVerdict, "预期标签不能为空");
        id = id.trim();
        claim = claim.trim();
        evidence = evidence.trim();
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (id.isBlank() || claim.isBlank() || evidence.isBlank()) {
            throw new IllegalArgumentException("评测样例 ID、结论和证据不能为空字符串");
        }
        if (expectedVerdict != ClaimEvidenceVerdict.SUPPORTED
                && expectedVerdict != ClaimEvidenceVerdict.CONTRADICTED
                && expectedVerdict != ClaimEvidenceVerdict.INSUFFICIENT) {
            throw new IllegalArgumentException("金标只允许 SUPPORTED、CONTRADICTED 或 INSUFFICIENT");
        }
    }
}
