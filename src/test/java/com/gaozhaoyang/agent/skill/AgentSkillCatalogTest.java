package com.gaozhaoyang.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSkillCatalogTest {

    @Test
    void shouldIndexMetadataWithoutActivatingInstructions() {
        AgentSkillCatalog catalog = catalog();

        assertThat(catalog.list())
                .extracting(AgentSkillSummary::name)
                .containsExactly(
                        "java-code-generation", "security-review", "test-failure-repair");
        assertThat(catalog.list())
                .allSatisfy(skill -> assertThat(skill.activationCount()).isZero());
    }

    @Test
    void shouldLoadOnlyRequestedSkillBodiesAndRecordActivation() {
        AgentSkillCatalog catalog = catalog();

        SkillActivation activation = catalog.activate(
                List.of("java-code-generation", "security-review"));

        assertThat(activation.skillNames())
                .containsExactly("java-code-generation", "security-review");
        assertThat(activation.instructions())
                .contains("# Java Code Generation", "# Security Review")
                .doesNotContain("# Test Failure Repair");
        assertThat(catalog.list())
                .filteredOn(skill -> activation.skillNames().contains(skill.name()))
                .allSatisfy(skill -> assertThat(skill.activationCount()).isEqualTo(1));
        assertThat(catalog.list())
                .filteredOn(skill -> skill.name().equals("test-failure-repair"))
                .singleElement()
                .satisfies(skill -> assertThat(skill.activationCount()).isZero());
    }

    private AgentSkillCatalog catalog() {
        return new AgentSkillCatalog("classpath*:agent-skills/*/SKILL.md");
    }
}
