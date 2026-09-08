package com.gaozhaoyang.agent.requirement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class RequirementCardValidator {

    private static final int MAX_BLOCKING_QUESTIONS = 4;

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

        List<String> risks =
                nullToEmpty(card.risks());

        List<String> references =
                nullToEmpty(card.references());

        List<ClarificationQuestion> clarificationQuestions =
                normalizeQuestions(card);
        List<String> missingInformation = clarificationQuestions.stream()
                .filter(ClarificationQuestion::blocking)
                .map(ClarificationQuestion::question)
                .toList();
        List<String> assumptions = new ArrayList<>(nullToEmpty(card.assumptions()));
        clarificationQuestions.stream()
                .filter(question -> !question.blocking())
                .filter(question -> !question.recommendedAnswer().isBlank())
                .map(question -> question.question() + "：默认采用“"
                        + question.recommendedAnswer() + "”")
                .forEach(assumptions::add);
        assumptions = assumptions.stream().distinct().toList();

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
                clarificationQuestions,
                assumptions,
                readyForPlanning
        );
    }

    private List<ClarificationQuestion> normalizeQuestions(RequirementCard card) {
        List<ClarificationQuestion> source = card.clarificationQuestions();
        if (source == null || source.isEmpty()) {
            source = nullToEmpty(card.missingInformation()).stream()
                    .map(question -> new ClarificationQuestion(
                            "业务规则", question, List.of(), "", true))
                    .toList();
        }

        Map<String, ClarificationQuestion> unique = new LinkedHashMap<>();
        int blockingCount = 0;
        for (ClarificationQuestion question : source) {
            if (question == null || question.question().isBlank()) {
                continue;
            }
            List<String> options = question.options();
            String recommendedAnswer = question.recommendedAnswer();
            if (recommendedAnswer.isBlank() && !options.isEmpty()) {
                recommendedAnswer = options.getFirst();
            }
            if (!recommendedAnswer.isBlank() && !options.contains(recommendedAnswer)) {
                List<String> expandedOptions = new ArrayList<>(options);
                expandedOptions.add(recommendedAnswer);
                options = List.copyOf(expandedOptions);
            }
            ClarificationQuestion normalized = new ClarificationQuestion(
                    question.category(), question.question(), options,
                    recommendedAnswer, question.blocking());
            if (question.blocking() && blockingCount >= MAX_BLOCKING_QUESTIONS) {
                continue;
            }
            String key = question.question().replaceAll("[？?。\\s]", "");
            if (!unique.containsKey(key)) {
                unique.put(key, normalized);
                if (normalized.blocking()) {
                    blockingCount++;
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
