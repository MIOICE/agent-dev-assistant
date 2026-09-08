package com.gaozhaoyang.agent.tool;

import com.gaozhaoyang.agent.knowledge.BusinessKnowledgeRetriever;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class BusinessDocumentTools {

    private final BusinessKnowledgeRetriever knowledgeRetriever;

    public BusinessDocumentTools(
            BusinessKnowledgeRetriever knowledgeRetriever
    ) {
        this.knowledgeRetriever = knowledgeRetriever;
    }

    @Tool(description = "根据需求关键词查询企业业务规范。涉及订单、导出、删除、批量修改、权限或敏感数据时应调用此工具。工具只读，不会修改任何业务数据。")
    public String searchBusinessDocument(
            @ToolParam(description = "用于查询业务规范的需求关键词或完整需求描述")
            String query) {
        if (query == null || query.isBlank()) {
            return "查询关键词不能为空";
        }

        List<Document> matches = knowledgeRetriever.search(query);

        if (matches.isEmpty()) {
            return "未找到与当前需求相关的业务规范";
        }

        return matches.stream()
                .map(document -> "[%s｜%s｜%s｜片段%s｜综合分%.3f｜向量%.3f]%n来源：%s%n%s".formatted(
                        document.getMetadata().get("sourceId"),
                        document.getMetadata().get("title"),
                        document.getMetadata().get("headingPath"),
                        document.getMetadata().get("chunkIndex"),
                        document.getScore(),
                        document.getMetadata().get("vectorScore"),
                        document.getMetadata().get("sourcePath"),
                        document.getText()
                ))
                .collect(Collectors.joining("\n\n"));
    }
}
