package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingDeliveryArtifactServiceTest {

    @Test
    void shouldBuildDeterministicVerifiableDeliveryBundle() throws Exception {
        Fixture fixture = fixture(CodingTaskStage.PUBLISHED);

        DeliveryArtifactBundle first = fixture.service.build("task-1");
        DeliveryArtifactBundle second = fixture.service.build("task-1");

        assertThat(first.content()).isEqualTo(second.content());
        assertThat(first.sha256()).isEqualTo(sha256(first.content()));
        assertThat(first.manifest().schemaVersion()).isEqualTo("agent-delivery-v1");
        assertThat(first.manifest().files()).hasSize(3);
        assertThat(first.manifest().verification().passed()).isTrue();
        assertThat(zipEntries(first.content())).containsExactly(
                "APPLYING.md",
                "changes/approved.patch",
                "delivery-manifest.json",
                "evidence/build-summary.txt",
                "project/pom.xml",
                "project/src/main/java/demo/generated/OrderExport.java",
                "project/src/test/java/demo/generated/OrderExportTest.java"
        );
    }

    @Test
    void shouldRejectArtifactWhenApprovedFileWasTampered() throws Exception {
        Fixture fixture = fixture(CodingTaskStage.PUBLISHED);
        Files.writeString(
                fixture.output.resolve("src/main/java/demo/generated/OrderExport.java"),
                "tampered",
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> fixture.service.build("task-1"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("完整性校验失败");
    }

    @Test
    void shouldRejectArtifactWhenBuildDescriptorWasTampered() throws Exception {
        Fixture fixture = fixture(CodingTaskStage.PUBLISHED);
        Files.writeString(
                fixture.output.resolve("pom.xml"),
                "<project>tampered</project>",
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> fixture.service.build("task-1"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("pom.xml");
    }

    @Test
    void shouldRejectDownloadBeforeHumanApproval() throws Exception {
        Fixture fixture = fixture(CodingTaskStage.WAITING_APPROVAL);

        assertThatThrownBy(() -> fixture.service.build("task-1"))
                .isInstanceOf(CodingTaskException.class)
                .hasMessageContaining("人工审批并发布");
    }

    private Fixture fixture(CodingTaskStage stage) throws IOException {
        Path approvedRoot = Path.of(
                ".codex-target", "delivery-tests", UUID.randomUUID().toString())
                .toAbsolutePath();
        Path output = approvedRoot.resolve("task-1");
        String mainPath = "src/main/java/demo/generated/OrderExport.java";
        String testPath = "src/test/java/demo/generated/OrderExportTest.java";
        String mainContent = "package demo.generated; public class OrderExport {}";
        String testContent = "package demo.generated; public class OrderExportTest {}";
        SandboxProjectTemplate projectTemplate = new SandboxProjectTemplate();
        projectTemplate.initialize(output);
        write(output.resolve(mainPath), mainContent);
        write(output.resolve(testPath), testContent);

        List<PatchFile> patches = List.of(
                patch(mainPath, mainContent),
                patch(testPath, testContent)
        );
        Instant now = Instant.parse("2026-09-12T02:00:00Z");
        AgentLoopState loop = AgentLoopState.initial(8).stop(
                stage == CodingTaskStage.PUBLISHED
                        ? AgentLoopStopCode.GOAL_REACHED
                        : AgentLoopStopCode.WAITING_HUMAN_APPROVAL,
                "ready"
        );
        CodingTask task = new CodingTask(
                "task-1", "workflow-1", null, stage, "订单导出补丁",
                AutonomyBudget.safeDefault(), 2,
                mainContent.getBytes(StandardCharsets.UTF_8).length
                        + testContent.getBytes(StandardCharsets.UTF_8).length,
                1, 20, 0,
                List.of("java-code-generation", "security-review"),
                patches,
                new BuildVerification(true, "mvn test", 0, 20, "tests passed"),
                List.of(), List.of(), loop,
                "workspace", output.toString(), "", now, now
        );
        CodingTaskService taskService = mock(CodingTaskService.class);
        when(taskService.get("task-1")).thenReturn(task);
        CodingDeliveryArtifactService service = new CodingDeliveryArtifactService(
                taskService, new SandboxPolicy(), projectTemplate, new ObjectMapper(),
                approvedRoot.toString());
        return new Fixture(service, output);
    }

    private PatchFile patch(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PatchFile(
                path, "test", "ADD", sha256(bytes), bytes.length,
                "+++ b/" + path + "\n+" + content);
    }

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private List<String> zipEntries(byte[] content) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(CodingDeliveryArtifactService service, Path output) {
    }
}
