package com.gaozhaoyang.agent.skill;

import com.gaozhaoyang.agent.knowledge.LocalHashEmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardCodingSkillProviderTest {

    @Test
    void shouldSelectExportAndSecuritySkillsForSensitiveBulkExport() {
        StandardCodingSkillProvider provider = provider(0.04);

        SkillActivation activation = provider.activate(
                CodingSkillPhase.GENERATION,
                "订单数据导出下载，大数据量使用异步任务，仅管理员有权限并记录审计");

        assertThat(activation.skillNames())
                .contains("java-code-generation", "export-reliability", "security-review")
                .doesNotContain("test-failure-repair");
        assertThat(activation.routingScores())
                .containsKeys("export-reliability", "security-review");
    }

    @Test
    void shouldKeepRepairSkillRequiredAndRespectPhaseBoundary() {
        StandardCodingSkillProvider provider = provider(0.90);

        SkillActivation activation = provider.activate(
                CodingSkillPhase.REPAIR,
                "编译失败：找不到符号 OrderExportPolicy");

        assertThat(activation.skillNames())
                .containsExactly("test-failure-repair");
        assertThat(activation.instructions())
                .contains("# Test Failure Repair")
                .doesNotContain("# Java Code Generation", "# Export Reliability");
    }

    private StandardCodingSkillProvider provider(double threshold) {
        return new StandardCodingSkillProvider(
                new AgentSkillCatalog("classpath*:agent-skills/*/SKILL.md"),
                new LocalHashEmbeddingModel(), threshold, 2);
    }
}
