package com.gaozhaoyang.agent.skill;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSkillCatalogTest {

    @Test
    void shouldIndexMetadataWithoutActivatingInstructions() {
        AgentSkillCatalog catalog = catalog();

        assertThat(catalog.list())
                .extracting(AgentSkillSummary::name)
                .containsExactly(
                        "export-reliability", "java-code-generation",
                        "security-review", "test-failure-repair");
        assertThat(catalog.list())
                .allSatisfy(skill -> {
                    assertThat(skill.activationCount()).isZero();
                    assertThat(skill.trusted()).isTrue();
                    assertThat(skill.sha256()).hasSize(64);
                    assertThat(skill.bytes()).isPositive();
                });
    }

    @Test
    void shouldFailFastWhenSkillDigestDoesNotMatchTrustedManifest() throws Exception {
        Path tempDirectory = Path.of(
                ".codex-target", "skill-integrity-tests", UUID.randomUUID().toString())
                .toAbsolutePath();
        Path skillDirectory = Files.createDirectories(
                tempDirectory.resolve("tampered-skill"));
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: tampered-skill
                description: 用于校验摘要不一致时必须拒绝启动。
                allowed-tools:
                  - sandbox-read
                metadata:
                  phases: GENERATION
                ---
                # Tampered
                """);
        Files.writeString(tempDirectory.resolve("manifest.sha256"),
                "0".repeat(64) + "  tampered-skill/SKILL.md\n");

        String pattern = tempDirectory.toUri() + "*/SKILL.md";
        String manifest = tempDirectory.resolve("manifest.sha256").toUri().toString();

        assertThatThrownBy(() -> new AgentSkillCatalog(pattern, manifest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA-256");
    }

    @Test
    void shouldRejectToolDeclarationOutsideHostAllowlist() throws Exception {
        Path root = Path.of(
                ".codex-target", "skill-policy-tests", UUID.randomUUID().toString())
                .toAbsolutePath();
        Path skillDirectory = Files.createDirectories(root.resolve("unsafe-skill"));
        String content = """
                ---
                name: unsafe-skill
                description: 尝试声明宿主未开放的高风险执行能力。
                allowed-tools:
                  - unrestricted-shell
                metadata:
                  phases: GENERATION
                ---
                # Unsafe
                """;
        Files.writeString(skillDirectory.resolve("SKILL.md"), content);
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(root.resolve("manifest.sha256"),
                digest + "  unsafe-skill/SKILL.md\n");

        assertThatThrownBy(() -> new AgentSkillCatalog(
                root.toUri() + "*/SKILL.md",
                root.resolve("manifest.sha256").toUri().toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("宿主未声明的能力");
    }

    @Test
    void shouldTreatWindowsAndUnixLineEndingsAsSameReviewedContent() throws Exception {
        Path root = Path.of(
                ".codex-target", "skill-line-ending-tests", UUID.randomUUID().toString())
                .toAbsolutePath();
        Path skillDirectory = Files.createDirectories(root.resolve("portable-skill"));
        String unixContent = """
                ---
                name: portable-skill
                description: 验证跨平台换行差异不会被错误识别为内容篡改。
                allowed-tools:
                  - sandbox-read
                metadata:
                  phases: GENERATION
                ---
                # Portable
                """;
        Files.writeString(
                skillDirectory.resolve("SKILL.md"), unixContent.replace("\n", "\r\n"));
        String reviewedDigest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(unixContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(root.resolve("manifest.sha256"),
                reviewedDigest + "  portable-skill/SKILL.md\n");

        AgentSkillCatalog catalog = new AgentSkillCatalog(
                root.toUri() + "*/SKILL.md",
                root.resolve("manifest.sha256").toUri().toString());

        assertThat(catalog.list()).singleElement()
                .satisfies(skill -> assertThat(skill.trusted()).isTrue());
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
