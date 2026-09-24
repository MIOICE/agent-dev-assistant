package com.gaozhaoyang.agent.requirement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveClarificationPolicyTest {

    private final AdaptiveClarificationPolicy policy = new AdaptiveClarificationPolicy();

    @Test
    void shouldRouteTechnicalImplementationQuestionToAgentDefault() {
        ClarificationQuestion technical = question(
                "其他",
                "接口失败后重试次数是多少？",
                List.of("重试一次", "重试三次"),
                "重试三次"
        );

        ClarificationQuestion routed = policy.route(List.of(technical)).getFirst();

        assertThat(routed.blocking()).isFalse();
        assertThat(routed.recommendedAnswer()).isEqualTo("重试三次");
        assertThat(routed.reason()).contains("技术实现");
    }

    @Test
    void shouldAskOnlyThreeHighestRiskBusinessQuestionsAndExplainThem() {
        List<ClarificationQuestion> routed = policy.route(List.of(
                question("时效", "希望什么时候上线？", List.of("本周", "下周"), "下周"),
                question("范围", "全量导出哪些订单？", List.of("筛选结果", "全部"), "筛选结果"),
                question("权限", "哪些角色可以导出？", List.of("沿用权限", "管理员"), "沿用权限"),
                question("业务规则", "删除后是否需要恢复？", List.of("逻辑删除", "物理删除"), "逻辑删除"),
                question("其他", "按钮放在哪里？", List.of("列表顶部", "更多菜单"), "更多菜单")
        ));

        List<ClarificationQuestion> blocking = routed.stream()
                .filter(ClarificationQuestion::blocking)
                .toList();

        assertThat(blocking).hasSize(3);
        assertThat(blocking)
                .extracting(ClarificationQuestion::question)
                .containsExactly(
                        "删除后是否需要恢复？",
                        "哪些角色可以导出？",
                        "全量导出哪些订单？"
                );
        assertThat(blocking)
                .allMatch(question -> !question.reason().isBlank())
                .allMatch(question -> !question.impact().isBlank());
        assertThat(routed.stream().filter(question -> !question.blocking()).toList())
                .hasSize(2)
                .allMatch(question -> !question.recommendedAnswer().isBlank());
    }

    @Test
    void shouldNeverSilentlyDefaultQuestionWithoutRecommendation() {
        List<ClarificationQuestion> routed = policy.route(List.of(
                question("业务规则", "问题1", List.of("A"), "A"),
                question("业务规则", "问题2", List.of("A"), "A"),
                question("业务规则", "问题3", List.of("A"), "A"),
                new ClarificationQuestion(
                        "业务规则", "问题4", List.of(), "", true, "必须确认", "影响业务结果")
        ));

        assertThat(routed.stream().filter(ClarificationQuestion::blocking).toList()).hasSize(4);
        assertThat(routed).anyMatch(question -> question.question().equals("问题4")
                && question.blocking());
    }

    private ClarificationQuestion question(
            String category,
            String question,
            List<String> options,
            String recommendedAnswer
    ) {
        return new ClarificationQuestion(
                category, question, options, recommendedAnswer, true, "", "");
    }
}
