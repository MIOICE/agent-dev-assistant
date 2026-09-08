package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.tool.BusinessDocument;
import com.gaozhaoyang.agent.tool.BusinessDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
            Resource resource,
            BusinessDocumentRepository documentRepository
    ) {
        this(mergeCases(
                readCases(objectMapper, resource),
                externalCases(documentRepository)
        ));
    }

    RetrievalEvaluationDataset(
            ObjectMapper objectMapper,
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

    private static List<RetrievalEvaluationCase> externalCases(
            BusinessDocumentRepository repository
    ) {
        List<BusinessDocument> externalDocuments = repository.findAll().stream()
                .filter(document -> "external-mes".equals(document.sourceType()))
                .sorted(Comparator.comparing(BusinessDocument::sourcePath))
                .toList();

        List<BusinessDocument> candidates = new ArrayList<>();
        candidates.addAll(documentsOfType(
                externalDocuments,
                "BUSINESS_PAGE",
                2
        ));
        candidates.addAll(documentsOfType(
                externalDocuments,
                "REQUIREMENT_ANALYSIS",
                2
        ));

        List<RetrievalEvaluationCase> evaluationCases = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            BusinessDocument document = candidates.get(index);
            evaluationCases.add(new RetrievalEvaluationCase(
                    "EVAL-MES-%03d".formatted(index + 1),
                    document.title(),
                    List.of(document.id())
            ));
        }
        return List.copyOf(evaluationCases);
    }

    private static List<BusinessDocument> documentsOfType(
            List<BusinessDocument> documents,
            String documentType,
            int limit
    ) {
        return documents.stream()
                .filter(document -> documentType.equals(document.documentType()))
                .limit(limit)
                .toList();
    }

    private static List<RetrievalEvaluationCase> mergeCases(
            List<RetrievalEvaluationCase> bundled,
            List<RetrievalEvaluationCase> external
    ) {
        List<RetrievalEvaluationCase> merged = new ArrayList<>(bundled);
        merged.addAll(external);
        return List.copyOf(merged);
    }
}
