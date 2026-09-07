package com.gaozhaoyang.agent.requirement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRequirementAnalyzerTest {

    private final RuleBasedRequirementAnalyzer analyzer = new RuleBasedRequirementAnalyzer();

    @Test
    void shouldBuildReadyCardForConcreteOrderRequirement() {
        RequirementCard card = analyzer.analyze("订单列表需要增加客户筛选，并支持导出报表");

        assertThat(card.affectedModules()).contains("订单管理", "报表管理");
        assertThat(card.acceptanceCriteria()).hasSize(3);
        assertThat(card.missingInformation()).isEmpty();
        assertThat(card.readyForPlanning()).isTrue();
    }

    @Test
    void shouldAskForMissingModuleWhenRequirementIsVague() {
        RequirementCard card = analyzer.analyze("希望使用起来更方便");

        assertThat(card.missingInformation()).isNotEmpty();
        assertThat(card.readyForPlanning()).isFalse();
    }

    @Test
void shouldMarkUrgentRequirementAsP0() {
    RequirementCard card =
            analyzer.analyze("紧急：订单列表增加客户筛选");

    assertThat(card.priority()).isEqualTo("P0");
}

@Test
void shouldMarkThisWeekRequirementAsP1() {
    RequirementCard card =
            analyzer.analyze("订单导出功能需要本周完成");

    assertThat(card.priority()).isEqualTo("P1");
}

@Test
void shouldUseP2AsDefaultPriority() {
    RequirementCard card =
            analyzer.analyze("订单列表需要增加客户筛选");

    assertThat(card.priority()).isEqualTo("P2");
}

@Test
void shouldDetectFullExportRisk() {
    RequirementCard card =
            analyzer.analyze("订单列表需要增加全量导出功能");

    assertThat(card.risks())
            .contains("全量导出可能造成数据库压力，需要确认数据范围");
}

@Test
void shouldDetectMultipleRisks() {
    RequirementCard card =
            analyzer.analyze("紧急批量修改订单并删除错误数据");

    assertThat(card.risks())
            .contains(
                    "删除操作可能导致数据丢失，需要确认恢复方案",
                    "批量修改影响范围较大，需要确认筛选条件和目标字段"
            );

    assertThat(card.priority()).isEqualTo("P0");
}

@Test
void shouldAskForPageWhenExportRequirementIsVague() {
    RequirementCard card = analyzer.analyze("增加导出功能");

    assertThat(card.missingInformation())
            .contains("请补充需要导出的页面或业务模块");

    assertThat(card.readyForPlanning()).isFalse();
}

@Test
void shouldAllowConcreteExportRequirementToEnterPlanning() {
    RequirementCard card =
            analyzer.analyze("订单列表需要增加全量导出功能");

    assertThat(card.affectedModules())
            .contains("订单管理");

    assertThat(card.missingInformation()).isEmpty();
    assertThat(card.readyForPlanning()).isTrue();
}

@Test
void shouldKeepOriginalTitleAfterClarification() {
    RequirementCard card = analyzer.analyze("""
            增加导出功能

            用户补充信息：
            - 导出订单列表当前筛选结果，文件格式为CSV
            """);

    assertThat(card.title()).isEqualTo("增加导出功能");
    assertThat(card.background()).contains("用户补充信息");
    assertThat(card.readyForPlanning()).isTrue();
}
}
