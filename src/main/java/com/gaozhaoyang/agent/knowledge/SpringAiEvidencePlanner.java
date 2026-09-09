package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "deepseek")
public class SpringAiEvidencePlanner implements EvidencePlanner {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEvidencePlanner.class);
    private static final String SYSTEM_PROMPT = """
            你是企业存量系统的证据检索规划器。你的任务不是生成技术方案，
            而是把需求拆成最少且可验证的检索问题。

            输出约束：
            - planningMode 固定为 MODEL。
            - needs 只能有1至5项，id必须唯一且使用大写英文下划线。
            - 优先使用 BUSINESS_RULES、DATA_AND_API、SECURITY、PERFORMANCE 作为证据类型 ID。
            - query 必须适合检索企业业务文档，最多300字。
            - purpose 说明该证据会支持什么决策，最多200字。
            - 业务规则、数据/接口依赖通常标为 required=true。
            - 只有需求确实涉及权限、批量、删除、导出或高数据量时才增加专项问题。
            - 不得编造表名、字段名、接口地址和业务事实。
            - 只返回符合目标Java对象结构的JSON。
            """;

    private final ChatClient chatClient;
    private final RuleBasedEvidencePlanner fallbackPlanner = new RuleBasedEvidencePlanner();

    public SpringAiEvidencePlanner(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public EvidencePlan plan(String effectiveRequirement, RequirementCard card) {
        try {
            EvidencePlan plan = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(user -> user.text("""
                            【完整需求】
                            {requirement}

                            【结构化需求卡片】
                            标题：{title}
                            涉及模块：{modules}
                            验收标准：{acceptanceCriteria}
                            风险：{risks}
                            """)
                            .param("requirement", effectiveRequirement)
                            .param("title", card.title())
                            .param("modules", String.join("、", card.affectedModules()))
                            .param("acceptanceCriteria", String.join("；", card.acceptanceCriteria()))
                            .param("risks", String.join("；", card.risks())))
                    .call()
                    .entity(EvidencePlan.class, specification -> specification.validateSchema());
            return plan.withPlanningMode("MODEL");
        } catch (RuntimeException exception) {
            log.warn("Model evidence planning failed; using bounded deterministic fallback", exception);
            return fallbackPlanner.plan(
                    effectiveRequirement,
                    card,
                    "RULE_BASED_FALLBACK"
            );
        }
    }
}
