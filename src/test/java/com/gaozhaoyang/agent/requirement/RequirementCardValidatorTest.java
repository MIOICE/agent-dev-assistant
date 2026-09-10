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

    @Test
    void shouldOnlyUseBlockingQuestionsToDecideReadiness() {
        RequirementCard modelResult = new RequirementCard(
                "订单导出",
                "订单列表增加导出",
                List.of("订单管理"),
                List.of("可以导出订单"),
                List.of("导出格式是什么？"),
                "P2",
                List.of(),
                List.of(),
                List.of(new ClarificationQuestion(
                        "其他",
                        "导出格式是什么？",
                        List.of("Excel", "CSV"),
                        "Excel",
                        false
                )),
                List.of(),
                false
        );

        RequirementCard validated = validator.validate(modelResult);

        assertThat(validated.readyForPlanning()).isTrue();
        assertThat(validated.missingInformation()).isEmpty();
        assertThat(validated.assumptions())
                .contains("导出格式是什么？：默认采用“Excel”");
    }

    @Test
    void shouldLimitBlockingQuestionsAndRepairRecommendationOptions() {
        List<ClarificationQuestion> questions = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> new ClarificationQuestion(
                        "业务规则",
                        "问题" + index,
                        List.of("选项A", "选项B"),
                        index == 1 ? "推荐选项" : "",
                        true
                ))
                .toList();
        RequirementCard modelResult = new RequirementCard(
                "批量操作",
                "批量操作需求",
                List.of("订单管理"),
                List.of(),
                List.of(),
                "P1",
                List.of(),
                List.of(),
                questions,
                List.of(),
                false
        );

        RequirementCard validated = validator.validate(modelResult);

        assertThat(validated.clarificationQuestions()).hasSize(6);
        assertThat(validated.missingInformation()).hasSize(3);
        assertThat(validated.assumptions()).hasSize(3);
        assertThat(validated.clarificationQuestions().getFirst().options())
                .contains("推荐选项");
        assertThat(validated.clarificationQuestions().get(1).recommendedAnswer())
                .isEqualTo("选项A");
        assertThat(validated.clarificationQuestions())
                .allMatch(question -> !question.reason().isBlank())
                .allMatch(question -> !question.impact().isBlank());
    }
}
