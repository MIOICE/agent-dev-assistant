package com.gaozhaoyang.agent.requirement;

import com.gaozhaoyang.agent.tool.BusinessDocumentTools;
import com.gaozhaoyang.agent.tool.DatabaseMetadataTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "app.ai.mode",
        havingValue = "deepseek"
)
public class SpringAiRequirementAnalyzer implements RequirementAnalyzer {

    private static final String SYSTEM_PROMPT = """
            你是企业MES/EAP项目的需求分析助手。

            你的任务是把用户的自然语言需求转换成结构化需求卡片。

            分析规则：

            1. title：用简短语言概括需求，不超过30个汉字。
            2. background：忠实复述需求背景，不得编造不存在的信息。
            3. affectedModules：识别可能涉及的业务模块。
            4. acceptanceCriteria：生成可以验证的验收标准。
            5. missingInformation：列出生成方案前必须补充的业务信息。
            6. priority：
               - 紧急、今天、立即处理为P0；
               - 本周、上线前处理为P1；
               - 其他情况处理为P2。
            7. risks：识别全量导出、删除、批量修改、权限、敏感数据和性能风险。
            8. readyForPlanning：
               - missingInformation为空时为true；
               - missingInformation不为空时为false。
            9. references：列出本次分析实际使用的工具资料或数据库元数据，格式为“来源ID｜名称”；
               没有调用工具或没有找到资料时返回空列表，不得编造不存在的来源。

            安全约束：

            - 不得编造用户未提供的页面、字段、数据表或业务规则。
            - 技术实现细节可以后续查询，不能全部作为缺失业务信息。
            - 涉及删除、写库、权限和敏感数据时，必须明确标记风险。
            - 涉及订单、导出、删除、批量修改、权限或敏感数据时，必须先调用
              searchBusinessDocument工具查询业务规范，再结合工具结果进行分析。
            - 涉及字段筛选、数据导出、批量修改、删除或表关系时，还必须调用
              queryDatabaseMetadata工具查询相关表、字段和索引，只能读取元数据。
            - 工具返回的是只读参考资料，不代表用户已经明确了具体需求；资料无法确定的内容仍应列入missingInformation。
            - 只返回符合目标Java对象结构的JSON，不要输出解释、Markdown或代码块。
            """;

    private final ChatClient chatClient;
    private final RequirementCardValidator validator;
    private final BusinessDocumentTools businessDocumentTools;
    private final DatabaseMetadataTools databaseMetadataTools;
    public SpringAiRequirementAnalyzer(
        ChatClient.Builder chatClientBuilder,
        RequirementCardValidator validator,
        BusinessDocumentTools businessDocumentTools,
        DatabaseMetadataTools databaseMetadataTools
    ) {
    this.chatClient = chatClientBuilder.build();
    this.validator = validator;
    this.businessDocumentTools = businessDocumentTools;
    this.databaseMetadataTools = databaseMetadataTools;
}

  @Override
public RequirementCard analyze(String content) {
    try {
        RequirementCard modelResult = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text("""
                        请分析下面的业务需求，并输出JSON格式的需求卡片：

                        {requirement}
                        """)
                        .param("requirement", content))
                .tools(businessDocumentTools,
                         databaseMetadataTools)
                .call()
                .entity(
                        RequirementCard.class,
                        specification -> specification.validateSchema()
                );

        return validator.validate(modelResult);
    } catch (RuntimeException exception) {
        throw new RequirementAnalysisException(
                "需求分析服务暂时不可用",
                exception
        );
    }
}
}
