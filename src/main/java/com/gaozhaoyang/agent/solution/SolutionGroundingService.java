package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.EvidenceNeed;
import com.gaozhaoyang.agent.knowledge.EvidenceQueryAttempt;
import com.gaozhaoyang.agent.knowledge.EvidenceResearchReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SolutionGroundingService {

    private static final int MAX_CITATIONS_PER_CLAIM = 3;

    public SolutionGroundingReport ground(
            TechnicalSolution solution,
            EvidenceResearchReport evidenceReport
    ) {
        if (solution == null) {
            return SolutionGroundingReport.empty();
        }
        EvidenceResearchReport safeReport = evidenceReport == null
                ? EvidenceResearchReport.empty()
                : evidenceReport;
        Set<String> availableEvidenceIds = safeReport.evidence().stream()
                .map(result -> result.id())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<GroundedSolutionClaim> claims = new ArrayList<>();

        addClaim(claims, "SUMMARY", 0, solution.summary(), false,
                citationsFor("SUMMARY", safeReport, availableEvidenceIds));
        addSection(claims, "BACKEND", solution.backendChanges(), safeReport, availableEvidenceIds);
        addSection(claims, "DATABASE", solution.databaseChanges(), safeReport, availableEvidenceIds);
        addSection(claims, "API", solution.apiDesign(), safeReport, availableEvidenceIds);
        addSection(claims, "SECURITY", solution.securityControls(), safeReport, availableEvidenceIds);
        addSection(claims, "PERFORMANCE", solution.performanceStrategy(), safeReport, availableEvidenceIds);
        addSection(claims, "TEST", solution.testPlan(), safeReport, availableEvidenceIds);
        addSection(claims, "ROLLBACK", solution.rollbackPlan(), safeReport, availableEvidenceIds);
        addAssumptions(claims, solution.assumptions());

        int linked = (int) claims.stream()
                .filter(claim -> claim.status() == ClaimGroundingStatus.EVIDENCE_LINKED)
                .count();
        int assumptions = (int) claims.stream()
                .filter(claim -> claim.status() == ClaimGroundingStatus.ASSUMPTION)
                .count();
        int unsupported = (int) claims.stream()
                .filter(claim -> claim.status() == ClaimGroundingStatus.UNSUPPORTED)
                .count();
        int factual = linked + unsupported;
        double rate = factual == 0 ? 0.0 : round((double) linked / factual);
        List<String> warnings = new ArrayList<>();
        warnings.add("证据绑定表示该结论关联到检索来源，不等同于已经证明自然语言蕴含。");
        if (!safeReport.sufficient()) {
            warnings.add("证据研究仍有必要证据缺口，技术方案必须经过人工复核。");
        }
        if (unsupported > 0) {
            warnings.add("有 " + unsupported + " 条方案结论未绑定可信证据，应补充资料或改写为明确假设。");
        }
        return new SolutionGroundingReport(
                claims,
                factual,
                linked,
                assumptions,
                unsupported,
                rate,
                safeReport.sufficient(),
                warnings
        );
    }

    private void addSection(
            List<GroundedSolutionClaim> claims,
            String section,
            List<String> values,
            EvidenceResearchReport report,
            Set<String> availableIds
    ) {
        List<String> citations = citationsFor(section, report, availableIds);
        List<String> safeValues = values == null ? List.of() : values;
        for (int index = 0; index < safeValues.size(); index++) {
            addClaim(claims, section, index, safeValues.get(index), false, citations);
        }
    }

    private void addAssumptions(List<GroundedSolutionClaim> claims, List<String> assumptions) {
        List<String> safeAssumptions = assumptions == null ? List.of() : assumptions;
        for (int index = 0; index < safeAssumptions.size(); index++) {
            addClaim(claims, "ASSUMPTION", index, safeAssumptions.get(index), true, List.of());
        }
    }

    private void addClaim(
            List<GroundedSolutionClaim> claims,
            String section,
            int index,
            String claim,
            boolean assumption,
            List<String> citations
    ) {
        if (claim == null || claim.isBlank()) {
            return;
        }
        ClaimGroundingStatus status = assumption
                ? ClaimGroundingStatus.ASSUMPTION
                : citations.isEmpty()
                        ? ClaimGroundingStatus.UNSUPPORTED
                        : ClaimGroundingStatus.EVIDENCE_LINKED;
        String explanation = switch (status) {
            case EVIDENCE_LINKED -> "已关联 " + citations.size() + " 条检索证据";
            case ASSUMPTION -> "方案已将该内容明确标记为待确认假设";
            case UNSUPPORTED -> "当前证据计划没有为该结论提供可验证来源";
        };
        claims.add(new GroundedSolutionClaim(
                section + "-" + (index + 1),
                section,
                index,
                claim.trim(),
                citations,
                status,
                explanation
        ));
    }

    private List<String> citationsFor(
            String section,
            EvidenceResearchReport report,
            Set<String> availableIds
    ) {
        if (availableIds.isEmpty()) {
            return List.of();
        }
        boolean compatibilityMode = "SINGLE_QUERY_COMPATIBILITY".equals(report.planningMode());
        LinkedHashSet<String> citations = new LinkedHashSet<>();
        for (EvidenceNeed need : report.plannedNeeds()) {
            if (!compatibilityMode && !matchesSection(section, need)) {
                continue;
            }
            report.attempts().stream()
                    .filter(attempt -> attempt.needId().equals(need.id()))
                    .map(EvidenceQueryAttempt::resultIds)
                    .flatMap(List::stream)
                    .filter(availableIds::contains)
                    .forEach(citations::add);
        }
        if ("SUMMARY".equals(section) && citations.isEmpty()) {
            citations.addAll(availableIds);
        }
        return citations.stream().limit(MAX_CITATIONS_PER_CLAIM).toList();
    }

    private boolean matchesSection(String section, EvidenceNeed need) {
        if ("SUMMARY".equals(section)) {
            return true;
        }
        String context = (need.id() + " " + need.query() + " " + need.purpose())
                .toLowerCase(Locale.ROOT);
        return switch (section) {
            case "BACKEND" -> containsAny(context, "business", "业务", "规则", "流程");
            case "DATABASE", "API" -> containsAny(
                    context, "data", "api", "数据", "接口", "字段", "依赖"
            );
            case "SECURITY" -> containsAny(
                    context, "security", "权限", "安全", "审计", "敏感"
            );
            case "PERFORMANCE" -> containsAny(
                    context, "performance", "性能", "异步", "大数据", "并发", "超时"
            );
            case "TEST", "ROLLBACK" -> true;
            default -> false;
        };
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
