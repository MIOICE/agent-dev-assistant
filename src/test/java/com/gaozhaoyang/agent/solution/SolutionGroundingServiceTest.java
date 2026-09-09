package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.EvidenceNeed;
import com.gaozhaoyang.agent.knowledge.EvidenceQueryAttempt;
import com.gaozhaoyang.agent.knowledge.EvidenceResearchReport;
import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionGroundingServiceTest {

    @Test
    void shouldBindClaimsToPlannedEvidenceAndExposeUnsupportedStatements() {
        EvidenceResearchReport evidence = new EvidenceResearchReport(
                "MODEL",
                List.of(
                        new EvidenceNeed("BUSINESS_RULES", "订单业务规则", "确认业务流程", true),
                        new EvidenceNeed("DATA_AND_API", "订单数据接口", "确认字段和接口", true),
                        new EvidenceNeed("SECURITY", "订单权限", "确认权限控制", true),
                        new EvidenceNeed("PERFORMANCE", "订单性能", "确认异步策略", false)
                ),
                List.of(
                        attempt("BUSINESS_RULES", "doc-business"),
                        attempt("DATA_AND_API", "doc-data"),
                        new EvidenceQueryAttempt(1, "SECURITY", "订单权限", List.of(), 0.0, false),
                        attempt("PERFORMANCE", "doc-performance")
                ),
                List.of(
                        result("doc-business", "订单业务规范"),
                        result("doc-data", "订单数据规范"),
                        result("doc-performance", "订单性能规范")
                ),
                List.of("SECURITY"),
                false,
                2,
                5,
                2,
                6
        );
        TechnicalSolution solution = new TechnicalSolution(
                "为订单导出增加受控的异步处理流程",
                List.of("增加订单导出应用服务"),
                List.of("确认订单字段与索引"),
                List.of("增加导出任务接口"),
                List.of("仅管理员可以导出"),
                List.of("超过阈值转异步任务"),
                List.of("覆盖权限和大数据量测试"),
                List.of("保留原流程开关"),
                List.of("实际权限编码需要人工确认")
        );

        SolutionGroundingReport report = new SolutionGroundingService().ground(solution, evidence);

        assertThat(report.totalFactualClaims()).isEqualTo(8);
        assertThat(report.evidenceLinkedClaims()).isEqualTo(7);
        assertThat(report.unsupportedClaims()).isEqualTo(1);
        assertThat(report.assumptionClaims()).isEqualTo(1);
        assertThat(report.groundingRate()).isEqualTo(0.875);
        assertThat(report.evidenceSufficient()).isFalse();
        assertThat(report.claims())
                .filteredOn(claim -> claim.section().equals("SECURITY"))
                .extracting(GroundedSolutionClaim::status)
                .containsExactly(ClaimGroundingStatus.UNSUPPORTED);
        assertThat(report.claims())
                .filteredOn(claim -> claim.section().equals("DATABASE"))
                .flatExtracting(GroundedSolutionClaim::evidenceIds)
                .containsExactly("doc-data");
        assertThat(report.warnings()).anyMatch(value -> value.contains("不等同于已经证明"));
    }

    private EvidenceQueryAttempt attempt(String needId, String resultId) {
        return new EvidenceQueryAttempt(
                1, needId, needId, List.of(resultId), 0.9, true
        );
    }

    private KnowledgeSearchResult result(String id, String title) {
        Document document = Document.builder()
                .id(id)
                .text(title + "正文")
                .metadata("sourceId", id)
                .metadata("title", title)
                .metadata("chunkIndex", 1)
                .metadata("keywords", List.of("订单"))
                .score(0.9)
                .build();
        return KnowledgeSearchResult.from(document);
    }
}
