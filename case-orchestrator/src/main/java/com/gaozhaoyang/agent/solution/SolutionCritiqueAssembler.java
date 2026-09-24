package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SolutionCritiqueAssembler {

    private static final int MAX_EVIDENCE_PER_ASSESSMENT = 3;

    public SolutionCritiqueReport assemble(
            String mode,
            SolutionGroundingReport grounding,
            List<KnowledgeSearchResult> evidence,
            List<ClaimEvidenceAssessment> candidates
    ) {
        SolutionGroundingReport safeGrounding = grounding == null
                ? SolutionGroundingReport.empty()
                : grounding;
        Set<String> availableEvidenceIds = new LinkedHashSet<>((evidence == null ? List.<KnowledgeSearchResult>of() : evidence)
                .stream().map(KnowledgeSearchResult::id).toList());
        Map<String, ClaimEvidenceAssessment> candidateByClaimId = new LinkedHashMap<>();
        for (ClaimEvidenceAssessment candidate : candidates == null
                ? List.<ClaimEvidenceAssessment>of()
                : candidates) {
            if (candidate != null && !candidate.claimId().isBlank()) {
                candidateByClaimId.putIfAbsent(candidate.claimId(), candidate);
            }
        }

        List<ClaimEvidenceAssessment> validated = new ArrayList<>();
        for (GroundedSolutionClaim claim : safeGrounding.claims()) {
            validated.add(validateClaim(claim, candidateByClaimId.get(claim.claimId()), availableEvidenceIds));
        }

        int supported = count(validated, ClaimEvidenceVerdict.SUPPORTED);
        int contradicted = count(validated, ClaimEvidenceVerdict.CONTRADICTED);
        int insufficient = count(validated, ClaimEvidenceVerdict.INSUFFICIENT);
        int notEvaluated = count(validated, ClaimEvidenceVerdict.NOT_EVALUATED);
        int assumptions = count(validated, ClaimEvidenceVerdict.ASSUMPTION);
        int factual = validated.size() - assumptions;
        double supportRate = factual == 0 ? 0.0 : round((double) supported / factual);
        boolean safeForApproval = factual > 0
                && supported == factual
                && contradicted == 0
                && insufficient == 0
                && notEvaluated == 0;

        List<String> warnings = new ArrayList<>();
        warnings.add("模型语义审查是辅助复核信号，不等同于事实证明，最终方案仍需人工审批。");
        if ("RULE_BASED_NOT_EVALUATED".equals(mode) || "RULE_BASED_FALLBACK".equals(mode)) {
            warnings.add("当前未执行模型语义判定，已保留证据引用并将事实性结论标记为待人工复核。");
        }
        if (contradicted > 0) {
            warnings.add("发现 " + contradicted + " 条可能与证据矛盾的结论，审批前必须处理。");
        }
        if (insufficient + notEvaluated > 0) {
            warnings.add("有 " + (insufficient + notEvaluated) + " 条结论证据不足或尚未完成语义审查。");
        }
        return new SolutionCritiqueReport(
                mode,
                validated,
                factual,
                supported,
                contradicted,
                insufficient,
                notEvaluated,
                assumptions,
                supportRate,
                safeForApproval,
                warnings
        );
    }

    private ClaimEvidenceAssessment validateClaim(
            GroundedSolutionClaim claim,
            ClaimEvidenceAssessment candidate,
            Set<String> availableEvidenceIds
    ) {
        if (claim.status() == ClaimGroundingStatus.ASSUMPTION) {
            return new ClaimEvidenceAssessment(
                    claim.claimId(), ClaimEvidenceVerdict.ASSUMPTION, 1.0,
                    "该内容已由方案显式标记为待确认假设。", List.of()
            );
        }
        if (claim.status() == ClaimGroundingStatus.UNSUPPORTED) {
            return new ClaimEvidenceAssessment(
                    claim.claimId(), ClaimEvidenceVerdict.INSUFFICIENT, 1.0,
                    "该结论没有绑定可供语义审查的检索证据。", List.of()
            );
        }
        if (candidate == null) {
            return new ClaimEvidenceAssessment(
                    claim.claimId(), ClaimEvidenceVerdict.NOT_EVALUATED, 0.0,
                    "语义审查未返回该结论的判定。", List.of()
            );
        }

        Set<String> allowedForClaim = new LinkedHashSet<>(claim.evidenceIds());
        List<String> validatedIds = candidate.evidenceIds().stream()
                .filter(allowedForClaim::contains)
                .filter(availableEvidenceIds::contains)
                .distinct()
                .limit(MAX_EVIDENCE_PER_ASSESSMENT)
                .toList();
        ClaimEvidenceVerdict verdict = candidate.verdict();
        if (verdict == ClaimEvidenceVerdict.ASSUMPTION) {
            verdict = ClaimEvidenceVerdict.INSUFFICIENT;
        }
        if ((verdict == ClaimEvidenceVerdict.SUPPORTED
                || verdict == ClaimEvidenceVerdict.CONTRADICTED)
                && validatedIds.isEmpty()) {
            verdict = ClaimEvidenceVerdict.INSUFFICIENT;
        }
        String rationale = candidate.rationale().isBlank()
                ? "模型未提供判定理由。"
                : candidate.rationale();
        return new ClaimEvidenceAssessment(
                claim.claimId(), verdict, candidate.confidence(), rationale, validatedIds
        );
    }

    private int count(List<ClaimEvidenceAssessment> assessments, ClaimEvidenceVerdict verdict) {
        return (int) assessments.stream().filter(item -> item.verdict() == verdict).count();
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
