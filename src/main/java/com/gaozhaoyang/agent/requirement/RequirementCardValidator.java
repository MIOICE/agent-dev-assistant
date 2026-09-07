package com.gaozhaoyang.agent.requirement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RequirementCardValidator {

    private static final Set<String> ALLOWED_PRIORITIES =
            Set.of("P0", "P1", "P2");

    public RequirementCard validate(RequirementCard card) {
        if (card == null) {
            throw new IllegalArgumentException("模型没有返回需求卡片");
        }

        if (card.title() == null || card.title().isBlank()) {
            throw new IllegalArgumentException("需求标题不能为空");
        }

        if (!ALLOWED_PRIORITIES.contains(card.priority())) {
            throw new IllegalArgumentException(
                    "不合法的需求优先级：" + card.priority()
            );
        }

        List<String> affectedModules =
                nullToEmpty(card.affectedModules());

        List<String> acceptanceCriteria =
                nullToEmpty(card.acceptanceCriteria());

        List<String> missingInformation =
                nullToEmpty(card.missingInformation());

        List<String> risks =
                nullToEmpty(card.risks());

        List<String> references =
                nullToEmpty(card.references());

        boolean readyForPlanning = missingInformation.isEmpty();

        return new RequirementCard(
                card.title(),
                card.background(),
                affectedModules,
                acceptanceCriteria,
                missingInformation,
                card.priority(),
                risks,
                references,
                readyForPlanning
        );
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
