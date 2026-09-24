package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPolicyTest {

    private final SandboxPolicy policy = new SandboxPolicy();

    @Test
    void shouldAcceptSmallJavaPatchWithMainAndTestFiles() {
        CodePatchPlan validated = policy.validate(new CodePatchPlan(
                "demo",
                List.of(
                        file("src/main/java/demo/generated/Foo.java", "class Foo {}"),
                        file("src/test/java/demo/generated/FooTest.java", "class FooTest {}")
                )
        ), AutonomyBudget.safeDefault());

        assertThat(validated.files()).hasSize(2);
    }

    @Test
    void shouldRejectPathTraversal() {
        Path root = testRoot();
        assertThatThrownBy(() -> policy.resolveContained(
                root,
                "../../outside.java"
        )).isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("安全相对路径");
    }

    @Test
    void shouldRejectHostProcessCapability() {
        assertThatThrownBy(() -> policy.validate(new CodePatchPlan(
                "unsafe",
                List.of(
                        file("src/main/java/demo/generated/Foo.java",
                                "class Foo { void run(){ Runtime.getRuntime(); } }"),
                        file("src/test/java/demo/generated/FooTest.java", "class FooTest {}")
                )
        ), AutonomyBudget.safeDefault()))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("禁止能力");
    }

    @Test
    void shouldEnforceFileBudget() {
        List<GeneratedFile> files = java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> file(
                        (index == 6 ? "src/test" : "src/main")
                                + "/java/demo/generated/F" + index + ".java",
                        "class F" + index + " {}"
                ))
                .toList();

        assertThatThrownBy(() -> policy.validate(
                new CodePatchPlan("too many", files),
                AutonomyBudget.safeDefault()
        )).hasMessageContaining("文件数超过自治预算");
    }

    private GeneratedFile file(String path, String content) {
        return new GeneratedFile(path, "test", content);
    }

    private Path testRoot() {
        return Path.of(".codex-target", "test-workspaces", UUID.randomUUID().toString())
                .toAbsolutePath();
    }
}
