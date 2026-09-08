package com.gaozhaoyang.agent.skill;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StandardCodingSkillProvider implements CodingSkillProvider {

    private final AgentSkillCatalog catalog;

    public StandardCodingSkillProvider(AgentSkillCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public SkillActivation activate(CodingSkillPhase phase) {
        return switch (phase) {
            case GENERATION -> catalog.activate(List.of(
                    "java-code-generation", "security-review"));
            case REPAIR -> catalog.activate(List.of(
                    "test-failure-repair", "security-review"));
        };
    }
}
