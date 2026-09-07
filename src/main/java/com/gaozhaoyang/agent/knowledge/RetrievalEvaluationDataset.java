package com.gaozhaoyang.agent.knowledge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RetrievalEvaluationDataset {

    private final List<RetrievalEvaluationCase> cases;

    @Autowired
    public RetrievalEvaluationDataset(
            ObjectMapper objectMapper,
            @Value("classpath:evaluation/retrieval-cases.json")
            Resource resource
    ) {
        this(readCases(objectMapper, resource));
    }

    RetrievalEvaluationDataset(List<RetrievalEvaluationCase> cases) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("检索评测集不能为空");
        }

        Set<String> ids = new HashSet<>();
        for (RetrievalEvaluationCase evaluationCase : cases) {
            if (!ids.add(evaluationCase.id())) {
                throw new IllegalArgumentException(
                        "检索评测题 ID 重复：" + evaluationCase.id()
                );
            }
        }
        this.cases = List.copyOf(cases);
    }

    public List<RetrievalEvaluationCase> findAll() {
        return cases;
    }

    private static List<RetrievalEvaluationCase> readCases(
            ObjectMapper objectMapper,
            Resource resource
    ) {
        try (var inputStream = resource.getInputStream()) {
            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<RetrievalEvaluationCase>>() {
                    }
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法读取检索评测集：" + resource.getDescription(),
                    exception
            );
        }
    }
}
