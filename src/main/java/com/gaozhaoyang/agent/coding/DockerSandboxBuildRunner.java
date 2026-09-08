package com.gaozhaoyang.agent.coding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DockerSandboxBuildRunner implements SandboxBuildRunner {

    private static final int MAX_OUTPUT_CHARS = 4_000;
    private static final String IMAGE = "maven:3.9.9-eclipse-temurin-21";
    private final Path mavenRepository;

    public DockerSandboxBuildRunner(
            @Value("${app.coding.maven-repository}") String mavenRepository
    ) {
        this.mavenRepository = Path.of(mavenRepository).toAbsolutePath().normalize();
    }

    @Override
    public BuildVerification verify(Path workspace, AutonomyBudget budget) {
        Instant startedAt = Instant.now();
        Path logFile = workspace.resolve(".agent-build.log");
        String containerName = "agent-sandbox-" + workspace.getFileName();
        List<String> command = dockerCommand(workspace, containerName);
        Process process = null;
        try {
            if (!Files.isDirectory(mavenRepository)) {
                throw new CodingTaskException("Maven只读依赖仓库不存在，无法离线验证");
            }
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            boolean finished = process.waitFor(budget.maxDurationMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                removeTimedOutContainer(containerName);
                return result(false, -1, startedAt,
                        "容器构建超过自治预算，已终止并清理", logFile);
            }
            int exitCode = process.exitValue();
            return result(exitCode == 0, exitCode, startedAt, "", logFile);
        } catch (IOException exception) {
            throw new CodingTaskException(
                    "无法启动Docker沙箱，请确认Docker Desktop已启动且验证镜像已准备",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            removeTimedOutContainer(containerName);
            throw new CodingTaskException("Docker沙箱验证被中断", exception);
        }
    }

    private List<String> dockerCommand(Path workspace, String containerName) {
        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--pull=never",
                "--name", containerName,
                "--network", "none",
                "--read-only",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--memory", "512m",
                "--cpus", "1",
                "--pids-limit", "128",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
                "--tmpfs", "/home/maven:rw,noexec,nosuid,size=32m",
                "--tmpfs", "/workspace/target:rw,noexec,nosuid,size=128m",
                "--mount", "type=bind,source=" + workspace.toAbsolutePath()
                        + ",target=/workspace,readonly",
                "--mount", "type=bind,source=" + mavenRepository
                        + ",target=/m2,readonly",
                "--workdir", "/workspace",
                IMAGE,
                "mvn", "-q", "-o", "-Dmaven.repo.local=/m2", "test"
        ));
        return List.copyOf(command);
    }

    private void removeTimedOutContainer(String containerName) {
        try {
            Process cleanup = new ProcessBuilder(
                    "docker", "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start();
            cleanup.waitFor(5, TimeUnit.SECONDS);
        } catch (IOException exception) {
            // Docker不可用时没有可清理的运行中容器。
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private BuildVerification result(
            boolean passed,
            int exitCode,
            Instant startedAt,
            String fallback,
            Path logFile
    ) throws IOException {
        String output = Files.exists(logFile)
                ? Files.readString(logFile, StandardCharsets.UTF_8)
                : fallback;
        if (output.isBlank()) {
            output = passed ? "隔离容器内 Maven 测试通过" : fallback;
        }
        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(output.length() - MAX_OUTPUT_CHARS);
        }
        return new BuildVerification(
                passed,
                "docker run --network none --read-only ... mvn -o test",
                exitCode,
                Math.max(Duration.between(startedAt, Instant.now()).toMillis(), 0),
                output
        );
    }
}
