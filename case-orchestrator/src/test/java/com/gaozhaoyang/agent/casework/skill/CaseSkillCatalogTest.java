package com.gaozhaoyang.agent.casework.skill;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class CaseSkillCatalogTest {

    @Test
    void shouldVerifyManifestAndLoadOnlyPhaseSpecificInstructions() {
        CaseSkillCatalog catalog = new CaseSkillCatalog(
                new PathMatchingResourcePatternResolver(),
                "classpath*:case-skills/*/SKILL.md",
                "classpath:case-skills/manifest.sha256"
        );

        assertThat(catalog.skills()).hasSize(2);
        assertThat(catalog.manifestHash()).hasSize(64);
        assertThat(catalog.instructions(CaseSkillPhase.CLARIFICATION))
                .contains("requirement-clarification@1.0.0")
                .doesNotContain("solution-evidence-review");
        assertThat(catalog.instructions(CaseSkillPhase.SOLUTION_REVIEW))
                .contains("solution-evidence-review@1.0.0")
                .doesNotContain("requirement-clarification");
    }
}
