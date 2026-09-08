package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "deepseek")
public class SpringAiCodePatchGenerator implements CodePatchGenerator {

    private static final String SYSTEM_PROMPT = """
            你是受约束的 Java 21 代码补丁生成器。
            根据已经人工审批的需求卡片和技术方案，生成一个可独立编译的小型实现与JUnit 5测试。

            强制约束：
            - 只能生成2至6个.java文件。
            - 业务代码只能位于src/main/java/demo/generated/。
            - 测试代码只能位于src/test/java/demo/generated/。
            - package必须是demo.generated。
            - 不使用Spring、数据库、网络、文件系统、反射、进程、环境变量或第三方依赖。
            - 至少生成一个业务类和一个JUnit 5测试类。
            - 不编造真实项目中的表名、接口或性能数据。
            - 只返回符合CodePatchPlan结构的JSON，不返回Markdown代码围栏。
            """;

    private final ChatClient chatClient;

    public SpringAiCodePatchGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public CodePatchPlan generate(WorkflowState workflow) {
        try {
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(user -> user.text("""
                            【原始需求】
                            {requirement}

                            【结构化需求卡片】
                            {requirementCard}

                            【已审批技术方案】
                            {technicalSolution}
                            """)
                            .param("requirement", workflow.effectiveRequirement())
                            .param("requirementCard", String.valueOf(workflow.requirementCard()))
                            .param("technicalSolution", String.valueOf(workflow.technicalSolution())))
                    .call()
                    .entity(CodePatchPlan.class, specification -> specification.validateSchema());
        } catch (RuntimeException exception) {
            throw new CodingTaskException("代码补丁生成失败", exception);
        }
    }
}
