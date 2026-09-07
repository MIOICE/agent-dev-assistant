package com.gaozhaoyang.agent.requirement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementCardValidatorTest {

    private final RequirementCardValidator validator =
            new RequirementCardValidator();

    @Test
    void shouldForceNotReadyWhenInformationIsMissing() {
        RequirementCard modelResult = new RequirementCard(
                "增加导出功能",
                "用户希望增加导出功能",
                List.of(),
                List.of("可以成功导出"),
                List.of("请补充导出页面"),
                "P2",
                List.of(),
                List.of("DOC-EXPORT-001｜数据导出规范"),
                true
        );

        RequirementCard validated = validator.validate(modelResult);

        assertThat(validated.readyForPlanning()).isFalse();
        assertThat(validated.references())
                .containsExactly("DOC-EXPORT-001｜数据导出规范");
    }

    @Test
    void shouldAllowPlanningWhenNoInformationIsMissing() {
        RequirementCard modelResult = new RequirementCard(
                "订单导出",
                "订单列表增加导出",
                List.of("订单管理"),
                List.of("可以成功导出"),
                List.of(),
                "P1",
                List.of(),
                List.of(),
                false
        );

        RequirementCard validated = validator.validate(modelResult);

        assertThat(validated.readyForPlanning()).isTrue();
    }

    @Test
    void shouldRejectInvalidPriority() {
        RequirementCard modelResult = new RequirementCard(
                "订单导出",
                "订单列表增加导出",
                List.of("订单管理"),
                List.of("可以成功导出"),
                List.of(),
                "HIGH",
                List.of(),
                List.of(),
                true
        );

        assertThatThrownBy(() -> validator.validate(modelResult))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不合法的需求优先级：HIGH");
    }

    @Test
    void shouldReplaceNullListsWithEmptyLists() {
        RequirementCard modelResult = new RequirementCard(
                "订单导出",
                "订单列表增加导出",
                null,
                null,
                null,
                "P2",
                null,
                null,
                false
        );

        RequirementCard validated = validator.validate(modelResult);

        assertThat(validated.affectedModules()).isEmpty();
        assertThat(validated.acceptanceCriteria()).isEmpty();
        assertThat(validated.missingInformation()).isEmpty();
        assertThat(validated.risks()).isEmpty();
        assertThat(validated.references()).isEmpty();
        assertThat(validated.readyForPlanning()).isTrue();
    }
}
