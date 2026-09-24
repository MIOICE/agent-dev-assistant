package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticEvidenceResearchServiceTest {

    @Test
    void shouldRewriteUnresolvedQueryAndStopWhenRequiredEvidenceIsCovered() {
        EvidencePlanner planner = (requirement, card) -> new EvidencePlan(
                "MODEL",
                List.of(
                        new EvidenceNeed("BUSINESS", "订单导出业务规则", "确认业务规则", true),
                        new EvidenceNeed("SECURITY", "订单导出权限", "确认权限约束", true)
                )
        );
        AtomicInteger securityCalls = new AtomicInteger();
        KnowledgeSearcher searcher = query -> {
            if (query.equals("订单导出业务规则")) {
                return List.of(document("business", "订单导出业务规范", 0.91));
            }
            if (query.equals("订单导出权限")) {
                securityCalls.incrementAndGet();
                return List.of();
            }
            securityCalls.incrementAndGet();
            return List.of(
                    document("security", "订单导出权限规范", 0.88),
                    document("business", "订单导出业务规范", 0.76)
            );
        };
        AgenticEvidenceResearchService service = new AgenticEvidenceResearchService(
                planner, searcher, 2, 6, 8
        );

        EvidenceResearchReport report = service.research(
                "紧急：订单列表增加全量导出",
                "紧急：订单列表增加全量导出，仅管理员可操作",
                readyCard()
        );

        assertThat(report.planningMode()).isEqualTo("MODEL");
        assertThat(report.roundsUsed()).isEqualTo(2);
        assertThat(report.queriesUsed()).isEqualTo(3);
        assertThat(report.attempts()).extracting(EvidenceQueryAttempt::satisfied)
                .containsExactly(true, false, true);
        assertThat(report.attempts().getLast().query())
                .isEqualTo("订单列表增加全量导出 订单管理 确认权限约束");
        assertThat(report.evidence()).extracting(KnowledgeSearchResult::id)
                .containsExactly("business", "security");
        assertThat(report.unresolvedNeedIds()).isEmpty();
        assertThat(report.sufficient()).isTrue();
        assertThat(securityCalls).hasValue(2);
    }

    @Test
    void shouldStopAtQueryBudgetAndExposeRequiredEvidenceGap() {
        EvidencePlanner planner = (requirement, card) -> new EvidencePlan(
                "MODEL",
                List.of(
                        new EvidenceNeed("ONE", "问题一", "目标一", true),
                        new EvidenceNeed("TWO", "问题二", "目标二", true),
                        new EvidenceNeed("THREE", "问题三", "目标三", true)
                )
        );
        AgenticEvidenceResearchService service = new AgenticEvidenceResearchService(
                planner, query -> List.of(), 2, 2, 8
        );

        EvidenceResearchReport report = service.research("需求", "需求", readyCard());

        assertThat(report.queriesUsed()).isEqualTo(2);
        assertThat(report.attempts()).hasSize(2);
        assertThat(report.unresolvedNeedIds()).containsExactly("ONE", "TWO", "THREE");
        assertThat(report.sufficient()).isFalse();
    }

    private RequirementCard readyCard() {
        return new RequirementCard(
                "订单列表增加全量导出",
                "订单列表需要支持全量导出",
                List.of("订单管理"),
                List.of("仅管理员可导出当前筛选结果"),
                List.of(),
                "P1",
                List.of("大数据量导出可能影响性能"),
                List.of(),
                true
        );
    }

    private Document document(String id, String title, double score) {
        return Document.builder()
                .id(id)
                .text(title + "正文")
                .metadata("sourceId", id)
                .metadata("title", title)
                .metadata("chunkIndex", 1)
                .metadata("keywords", List.of("订单", "导出"))
                .score(score)
                .build();
    }
}
