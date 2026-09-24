package com.gaozhaoyang.agent.requirement;

import java.util.List;

/**
 * 面向业务用户的结构化澄清问题。
 * blocking=true 表示不确认就不能安全进入方案阶段；否则由 Agent 使用推荐值形成假设。
 */
public record ClarificationQuestion(
        String category,
        String question,
        List<String> options,
        String recommendedAnswer,
        boolean blocking,
        String reason,
        String impact
) {
    public ClarificationQuestion {
        category = category == null || category.isBlank() ? "其他" : category.trim();
        question = question == null ? "" : question.trim();
        options = options == null ? List.of() : options.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        recommendedAnswer = recommendedAnswer == null ? "" : recommendedAnswer.trim();
        reason = reason == null ? "" : reason.trim();
        impact = impact == null ? "" : impact.trim();
    }

    /** 保持已有测试、旧代码和历史 JSON 的兼容性。 */
    public ClarificationQuestion(
            String category,
            String question,
            List<String> options,
            String recommendedAnswer,
            boolean blocking
    ) {
        this(category, question, options, recommendedAnswer, blocking, "", "");
    }
}
