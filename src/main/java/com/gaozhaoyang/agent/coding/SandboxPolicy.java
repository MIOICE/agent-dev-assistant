package com.gaozhaoyang.agent.coding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SandboxPolicy {

    private static final List<String> ALLOWED_PREFIXES = List.of(
            "src/main/java/demo/generated/",
            "src/test/java/demo/generated/"
    );
    private static final List<String> FORBIDDEN_CODE = List.of(
            "Runtime.getRuntime",
            "ProcessBuilder",
            "System.exit",
            "System.getenv",
            "System.getProperty",
            "Class.forName",
            "java.io.",
            "java.nio.file",
            "java.net.",
            "javax.net.",
            "sun.misc.Unsafe"
    );

    public CodePatchPlan validate(CodePatchPlan plan, AutonomyBudget budget) {
        if (plan == null || plan.files().isEmpty()) {
            throw new CodingTaskException("代码生成结果没有包含文件");
        }
        if (plan.files().size() > budget.maxFiles()) {
            throw new CodingTaskException("生成文件数超过自治预算：" + budget.maxFiles());
        }

        Set<String> uniquePaths = new HashSet<>();
        long totalBytes = 0;
        boolean hasMain = false;
        boolean hasTest = false;
        for (GeneratedFile file : plan.files()) {
            String path = normalizeRelativePath(file.relativePath());
            if (!uniquePaths.add(path)) {
                throw new CodingTaskException("代码生成结果包含重复路径：" + path);
            }
            if (ALLOWED_PREFIXES.stream().noneMatch(path::startsWith) || !path.endsWith(".java")) {
                throw new CodingTaskException("文件不在允许的Java目录中：" + path);
            }
            if (file.content() == null || file.content().isBlank()) {
                throw new CodingTaskException("生成文件内容不能为空：" + path);
            }
            FORBIDDEN_CODE.stream()
                    .filter(file.content()::contains)
                    .findFirst()
                    .ifPresent(token -> {
                        throw new CodingTaskException("生成代码包含禁止能力：" + token);
                    });
            totalBytes += file.content().getBytes(StandardCharsets.UTF_8).length;
            hasMain |= path.startsWith("src/main/");
            hasTest |= path.startsWith("src/test/");
        }
        if (!hasMain || !hasTest) {
            throw new CodingTaskException("补丁必须同时包含业务代码和测试代码");
        }
        if (totalBytes > budget.maxTotalBytes()) {
            throw new CodingTaskException("生成内容超过自治预算：" + budget.maxTotalBytes() + "字节");
        }
        return new CodePatchPlan(plan.summary(), plan.files().stream()
                .map(file -> new GeneratedFile(
                        normalizeRelativePath(file.relativePath()),
                        file.purpose() == null ? "" : file.purpose().trim(),
                        file.content()
                ))
                .toList());
    }

    public Path resolveContained(Path root, String relativePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(normalizeRelativePath(relativePath)).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new CodingTaskException("路径越过沙箱边界：" + relativePath);
        }
        if (Files.isSymbolicLink(normalizedRoot)) {
            throw new CodingTaskException("沙箱根目录不能是符号链接");
        }
        Path current = normalizedRoot;
        Path parent = candidate.getParent();
        if (parent != null) {
            for (Path part : normalizedRoot.relativize(parent)) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    throw new CodingTaskException("沙箱路径不能经过符号链接");
                }
            }
        }
        return candidate;
    }

    private String normalizeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw new CodingTaskException("生成文件路径不能为空");
        }
        String normalized = value.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("../") || normalized.equals("..")) {
            throw new CodingTaskException("生成文件必须使用安全相对路径：" + value);
        }
        return normalized;
    }
}
