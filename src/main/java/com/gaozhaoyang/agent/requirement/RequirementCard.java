package com.gaozhaoyang.agent.requirement;

import java.util.List;

public record RequirementCard(
        String title,
        String background,
        List<String> affectedModules,
        List<String> acceptanceCriteria,
        List<String> missingInformation,
        String priority,
        List<String> risks,
        List<String> references,
        List<ClarificationQuestion> clarificationQuestions,
        List<String> assumptions,
        boolean readyForPlanning
) {
    public RequirementCard {
        affectedModules = safeCopy(affectedModules);
        acceptanceCriteria = safeCopy(acceptanceCriteria);
        missingInformation = safeCopy(missingInformation);
        risks = safeCopy(risks);
        references = safeCopy(references);
        clarificationQuestions = clarificationQuestions == null
                ? List.of()
                : List.copyOf(clarificationQuestions);
        assumptions = safeCopy(assumptions);
    }

    /** 保持旧调用与数据库中旧 JSON 的兼容性。 */
    public RequirementCard(
            String title,
            String background,
            List<String> affectedModules,
            List<String> acceptanceCriteria,
            List<String> missingInformation,
            String priority,
            List<String> risks,
            List<String> references,
            boolean readyForPlanning
    ) {
        this(title, background, affectedModules, acceptanceCriteria, missingInformation,
                priority, risks, references, List.of(), List.of(), readyForPlanning);
    }

    private static List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
