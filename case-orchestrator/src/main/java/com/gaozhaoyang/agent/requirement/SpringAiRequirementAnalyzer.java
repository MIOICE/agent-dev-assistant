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
            5. clarificationQuestions：生成面向业务人员的结构化澄清问题：
               - category只能使用“范围、字段、权限、时效、业务规则、其他”；
               - question使用业务语言，不能询问线程池、索引、重试次数、分页大小等技术实现；
               - options提供2到4个简短选项；
               - recommendedAnswer必须是options中的一个，并给出稳妥的企业默认值；
               - blocking=true只用于不确认就可能做错业务、越权或造成不可逆影响的问题；
               - reason说明“为什么必须问”，impact说明“不同选择会影响什么”；
               - 每轮优先生成2到3个最高风险的blocking问题，避免一次向用户提出过多问题。
            6. missingInformation：仅用于兼容旧接口，内容必须等于所有blocking问题的question。
            7. assumptions：记录无需阻塞用户的推断和技术默认值，例如异步阈值、分页、重试、日志与编码方式。
            8. priority：
               - 紧急、今天、立即处理为P0；
               - 本周、上线前处理为P1；
               - 其他情况处理为P2。
            9. risks：识别全量导出、删除、批量修改、权限、敏感数据和性能风险。
            10. readyForPlanning：没有blocking问题时为true，否则为false；非阻塞假设不影响进入方案阶段。
            11. references：列出本次分析实际使用的工具资料或数据库元数据，格式为“来源ID｜名称”；
               没有调用工具或没有找到资料时返回空列表，不得编造不存在的来源。

            安全约束：

            - 不得编造用户未提供的页面、字段、数据表或业务规则。
            - 技术实现细节必须由Agent给出推荐默认值并放入assumptions，不能作为阻塞问题。
            - 能从工具资料、现有权限或当前页面规则推断的信息直接形成assumptions，不得再次要求业务用户确认。
            - 用户已明确接受的推荐值视为已确认，不得换一种说法重复追问。
            - 不主动追问通知文案、水印、快照保留期等次要偏好，除非业务规范明确要求。
            - 涉及删除、写库、权限和敏感数据时，必须明确标记风险。
            - 涉及订单、导出、删除、批量修改、权限或敏感数据时，必须先调用
              searchBusinessDocument工具查询业务规范，再结合工具结果进行分析。
            - 涉及字段筛选、数据导出、批量修改、删除或表关系时，还必须调用
              queryDatabaseMetadata工具查询相关表、字段和索引，只能读取元数据。
            - 工具返回的是只读参考资料。只有会改变业务结果、权限边界或造成不可逆影响的未知信息才是blocking；其余内容形成assumptions。
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
