package com.gaozhaoyang.agent.solution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Component
public class ClaimEvidenceEvaluationDataset {

    private final List<ClaimEvidenceEvaluationCase> cases;

    @Autowired
    public ClaimEvidenceEvaluationDataset(
            ObjectMapper objectMapper,
            @Value("classpath:evaluation/claim-evidence-cases.json") Resource resource
    ) {
        this(readCases(objectMapper, resource));
    }

    ClaimEvidenceEvaluationDataset(List<ClaimEvidenceEvaluationCase> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("Claim-Evidence 评测集不能为空");
        }
        Set<String> ids = new HashSet<>();
        for (ClaimEvidenceEvaluationCase evaluationCase : cases) {
            if (!ids.add(evaluationCase.id())) {
                throw new IllegalArgumentException(
                        "Claim-Evidence 评测样例 ID 重复：" + evaluationCase.id()
                );
            }
        }
        this.cases = List.copyOf(cases);
    }

    public List<ClaimEvidenceEvaluationCase> findAll() {
        return cases;
    }

    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ClaimEvidenceEvaluationCase evaluationCase : cases) {
                String canonical = String.join("\u001f",
                        evaluationCase.id(),
                        evaluationCase.claim(),
                        evaluationCase.evidence(),
                        evaluationCase.expectedVerdict().name(),
                        String.join("\u001e", evaluationCase.tags())
                );
                digest.update(canonical.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private static List<ClaimEvidenceEvaluationCase> readCases(
            ObjectMapper objectMapper,
            Resource resource
    ) {
        try (var inputStream = resource.getInputStream()) {
            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<ClaimEvidenceEvaluationCase>>() {
                    }
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法读取 Claim-Evidence 评测集：" + resource.getDescription(),
                    exception
            );
        }
    }
}
