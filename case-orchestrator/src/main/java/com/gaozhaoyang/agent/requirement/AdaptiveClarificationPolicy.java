package com.gaozhaoyang.agent.requirement;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把模型候选问题路由为“业务用户必须选择”或“Agent安全默认”。
 * 模型负责语义理解，Java策略负责数量、优先级和技术问题降级。
 */
@Component
public class AdaptiveClarificationPolicy {

    static final int MAX_USER_QUESTIONS = 3;

    private static final List<String> TECHNICAL_KEYWORDS = List.of(
            "线程池", "索引", "重试次数", "分页大小", "批次大小", "超时时间",
            "缓存", "消息队列", "异步阈值", "接口协议", "数据库类型", "代码结构"
    );

    public List<ClarificationQuestion> route(List<ClarificationQuestion> questions) {
        List<RankedQuestion> ranked = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            ClarificationQuestion enriched = enrich(questions.get(index));
            if (enriched.blocking() && isTechnical(enriched.question())) {
                enriched = asAgentDefault(
                        enriched,
                        "该项属于技术实现，交由研发依据现有系统规范确定",
                        true
                );
            }
            ranked.add(new RankedQuestion(index, score(enriched), enriched));
        }

        Set<Integer> selectedBlockingIndexes = new HashSet<>();
        ranked.stream()
                .filter(item -> item.question().blocking())
                .sorted(Comparator.comparingInt(RankedQuestion::score).reversed()
                        .thenComparingInt(RankedQuestion::index))
                .limit(MAX_USER_QUESTIONS)
                .map(RankedQuestion::index)
                .forEach(selectedBlockingIndexes::add);

        List<RankedQuestion> userQuestions = new ArrayList<>();
        List<RankedQuestion> agentDefaults = new ArrayList<>();
        for (RankedQuestion item : ranked) {
            ClarificationQuestion question = item.question();
            if (question.blocking() && selectedBlockingIndexes.contains(item.index())) {
                userQuestions.add(item);
            } else if (question.blocking() && question.recommendedAnswer().isBlank()) {
                // 极少数旧数据没有可用默认值，不能为了数量限制静默越过安全边界。
                userQuestions.add(item);
            } else if (question.blocking()) {
                agentDefaults.add(new RankedQuestion(
                        item.index(),
                        item.score(),
                        asAgentDefault(
                                question,
                                "为减少本轮输入，暂按推荐值形成可复核假设",
                                false
                        )
                ));
            } else {
                agentDefaults.add(item);
            }
        }
        userQuestions.sort(Comparator.comparingInt(RankedQuestion::score).reversed()
                .thenComparingInt(RankedQuestion::index));
        agentDefaults.sort(Comparator.comparingInt(RankedQuestion::index));
        List<ClarificationQuestion> result = new ArrayList<>();
        userQuestions.forEach(item -> result.add(item.question()));
        agentDefaults.forEach(item -> result.add(item.question()));
        return List.copyOf(result);
    }

    private ClarificationQuestion enrich(ClarificationQuestion question) {
        String reason = question.reason().isBlank()
                ? defaultReason(question.category())
                : question.reason();
        String impact = question.impact().isBlank()
                ? defaultImpact(question.category())
                : question.impact();
        return new ClarificationQuestion(
                question.category(),
                question.question(),
                question.options(),
                question.recommendedAnswer(),
                question.blocking(),
                reason,
                impact
        );
    }

    private ClarificationQuestion asAgentDefault(
            ClarificationQuestion question,
            String fallbackReason,
            boolean overrideReason
    ) {
        String recommended = question.recommendedAnswer().isBlank()
                ? technicalDefault(question.question())
                : question.recommendedAnswer();
        List<String> options = question.options();
        if (!options.contains(recommended)) {
            List<String> expanded = new ArrayList<>(options);
            expanded.add(recommended);
            options = List.copyOf(expanded);
        }
        return new ClarificationQuestion(
                question.category(),
                question.question(),
                options,
                recommended,
                false,
                overrideReason || question.reason().isBlank() ? fallbackReason : question.reason(),
                question.impact()
        );
    }

    private boolean isTechnical(String question) {
        return TECHNICAL_KEYWORDS.stream().anyMatch(question::contains);
    }

    private int score(ClarificationQuestion question) {
        if (!question.blocking()) {
            return 0;
        }
        int categoryScore = switch (question.category()) {
            case "业务规则" -> 80;
            case "权限" -> 75;
            case "范围" -> 65;
            case "字段" -> 55;
            case "时效" -> 45;
            default -> 30;
        };
        String text = question.question();
        if (containsAny(text, "删除", "不可恢复", "越权", "敏感")) {
            categoryScore += 30;
        } else if (containsAny(text, "全部", "全量", "批量", "角色")) {
            categoryScore += 20;
        }
        return categoryScore;
    }

    private String defaultReason(String category) {
        return switch (category) {
            case "范围" -> "当前描述无法唯一确定功能作用的数据范围";
            case "字段" -> "当前描述没有给出业务需要查看或处理的信息项";
            case "权限" -> "企业数据操作必须明确允许执行的用户范围";
            case "时效" -> "交付时间会影响需求优先级和处理方式";
            case "业务规则" -> "不同选择会改变真实业务结果或数据状态";
            default -> "该信息会影响需求边界，现有描述无法安全推断";
        };
    }

    private String defaultImpact(String category) {
        return switch (category) {
            case "范围" -> "影响处理对象、数据权限和最终验收范围";
            case "字段" -> "影响页面、接口、导出内容和数据校验";
            case "权限" -> "影响访问控制、敏感数据暴露和操作审计";
            case "时效" -> "影响优先级、资源安排和上线计划";
            case "业务规则" -> "影响数据正确性、可恢复性和后续流程";
            default -> "影响方案假设和验收标准";
        };
    }

    private String technicalDefault(String question) {
        if (question.contains("分页")) {
            return "沿用现有页面分页规则";
        }
        if (question.contains("重试")) {
            return "由研发采用有界重试并记录失败原因";
        }
        return "由研发依据现有系统规范确定";
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record RankedQuestion(int index, int score, ClarificationQuestion question) {
    }
}
