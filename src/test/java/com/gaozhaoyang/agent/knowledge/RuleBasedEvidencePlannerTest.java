package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedEvidencePlannerTest {

    @Test
    void shouldAddSecurityAndPerformanceEvidenceOnlyWhenRequirementNeedsThem() {
        RequirementCard card = new RequirementCard(
                "订单列表增加全量导出",
                "仅管理员可以执行全量导出",
                List.of("订单管理"),
                List.of("超过一万条时转为异步任务"),
                List.of(),
                "P0",
                List.of("敏感数据越权风险", "全量导出性能风险"),
                List.of(),
                true
        );

        EvidencePlan plan = new RuleBasedEvidencePlanner().plan(
                "紧急：订单列表增加全量导出，仅管理员可操作",
                card
        );

        assertThat(plan.planningMode()).isEqualTo("RULE_BASED");
        assertThat(plan.needs()).extracting(EvidenceNeed::id)
                .containsExactly("BUSINESS_RULES", "DATA_AND_API", "SECURITY", "PERFORMANCE");
        assertThat(plan.needs()).filteredOn(EvidenceNeed::required).hasSize(3);
        assertThat(plan.needs()).hasSizeLessThanOrEqualTo(5);
    }
}
