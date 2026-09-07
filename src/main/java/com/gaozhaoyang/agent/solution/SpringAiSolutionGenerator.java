package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        name = "app.ai.mode",
        havingValue = "deepseek"
)
public class SpringAiSolutionGenerator implements SolutionGenerator {

    private static final String SYSTEM_PROMPT = """
            你是企业Java后端技术方案设计助手。

            请严格依据需求卡片和检索资料生成结构化技术方案：

            1. summary：简要说明总体实现思路。
            2. backendChanges：需要调整的后端职责和处理流程。
            3. databaseChanges：数据库影响；无法确认表名或字段时必须明确写待确认，不得编造。
            4. apiDesign：接口输入、输出、异常和幂等设计。
            5. securityControls：权限、数据范围、敏感信息和审计措施。
            6. performanceStrategy：分页、异步、流式处理、限流或缓存方案。
            7. testPlan：可执行的测试场景。
            8. rollbackPlan：功能与数据库变更的回滚方式。
            9. assumptions：方案依赖但尚未由用户或资料确认的信息。

            安全约束：

            - 只能使用输入中明确提供的信息。
            - 不得编造接口地址、数据库表、字段、权限编码或性能数据。
            - 检索资料只能作为参考，资料之间冲突时必须写入assumptions。
            - 涉及删除、批量修改、敏感数据或高数据量时，必须设计人工审批、审计和回滚。
            - 只返回符合目标Java对象结构的JSON，不要返回Markdown或解释文字。
            """;

    private final ChatClient chatClient;

    public SpringAiSolutionGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public TechnicalSolution generate(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults
    ) {
        return generateInternal(requirementCard, knowledgeResults, List.of());
    }

    @Override
    public TechnicalSolution regenerate(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults,
            List<String> reviewerFeedback
    ) {
        return generateInternal(requirementCard, knowledgeResults, reviewerFeedback);
    }

    private TechnicalSolution generateInternal(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults,
            List<String> reviewerFeedback
    ) {
        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(user -> user.text("""
                            请根据下面的信息生成技术方案。

                            【需求卡片】
                            {requirementCard}

                            【检索到的业务资料】
                            {knowledgeContext}

                            【历次人工审批意见】
                            {reviewerFeedback}
                            """)
                            .param("requirementCard", formatRequirementCard(requirementCard))
                            .param("knowledgeContext", formatKnowledge(knowledgeResults))
                            .param("reviewerFeedback", formatFeedback(reviewerFeedback)))
                    .call()
                    .entity(
                            TechnicalSolution.class,
                            specification -> specification.validateSchema()
                    );
        } catch (RuntimeException exception) {
            throw new SolutionGenerationException(
                    "技术方案生成服务暂时不可用",
                    exception
            );
        }
    }

    private String formatFeedback(List<String> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return "无。本次为首次生成。";
        }
        return feedback.stream()
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
    }

    private String formatRequirementCard(RequirementCard card) {
        return """
                标题：%s
                背景：%s
                涉及模块：%s
                验收标准：%s
                缺失信息：%s
                优先级：%s
                风险：%s
                已引用资料：%s
                是否可进入方案阶段：%s
                """.formatted(
                card.title(),
                card.background(),
                String.join("、", card.affectedModules()),
                String.join("；", card.acceptanceCriteria()),
                String.join("；", card.missingInformation()),
                card.priority(),
                String.join("；", card.risks()),
                String.join("、", card.references()),
                card.readyForPlanning()
        );
    }

    private String formatKnowledge(List<KnowledgeSearchResult> results) {
        if (results.isEmpty()) {
            return "未检索到可引用的业务资料。";
        }
        return results.stream()
                .map(result -> """
                        来源：%s｜%s｜Chunk %d｜相似度 %.3f
                        内容：%s
                        """.formatted(
                        result.sourceId(),
                        result.title(),
                        result.chunkIndex(),
                        result.score(),
                        result.content()
                ))
                .collect(Collectors.joining("\n"));
    }
}
