package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class RuleBasedCodePatchGenerator implements CodePatchGenerator {

    @Override
    public CodePatchPlan generate(WorkflowState workflow) {
        return new CodePatchPlan(
                "在隔离演示工程中增加订单导出决策策略及对应单元测试，验证权限、格式和异步阈值。",
                List.of(
                        new GeneratedFile(
                                "src/main/java/demo/generated/OrderExportPolicy.java",
                                "订单导出的权限、格式与异步阈值决策",
                                sourceCode()
                        ),
                        new GeneratedFile(
                                "src/test/java/demo/generated/OrderExportPolicyTest.java",
                                "验证管理员权限、导出格式和一万条异步阈值",
                                testCode()
                        )
                )
        );
    }

    private String sourceCode() {
        return """
                package demo.generated;

                import java.util.Locale;

                public final class OrderExportPolicy {

                    public Decision decide(long estimatedRows, String requestedFormat, boolean administrator) {
                        if (!administrator) {
                            throw new SecurityException("仅管理员可以发起订单导出");
                        }
                        if (estimatedRows < 0) {
                            throw new IllegalArgumentException("预估数据量不能为负数");
                        }
                        if (requestedFormat == null || requestedFormat.isBlank()) {
                            throw new IllegalArgumentException("导出格式不能为空");
                        }
                        Format format = Format.valueOf(requestedFormat.trim().toUpperCase(Locale.ROOT));
                        return new Decision(estimatedRows > 10_000, format);
                    }

                    public enum Format {
                        CSV,
                        EXCEL
                    }

                    public record Decision(boolean asynchronous, Format format) {
                    }
                }
                """;
    }

    private String testCode() {
        return """
                package demo.generated;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertFalse;
                import static org.junit.jupiter.api.Assertions.assertThrows;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                class OrderExportPolicyTest {

                    private final OrderExportPolicy policy = new OrderExportPolicy();

                    @Test
                    void shouldUseAsyncModeWhenRowsExceedTenThousand() {
                        OrderExportPolicy.Decision decision = policy.decide(10_001, "csv", true);
                        assertTrue(decision.asynchronous());
                        assertEquals(OrderExportPolicy.Format.CSV, decision.format());
                    }

                    @Test
                    void shouldKeepSmallExportSynchronous() {
                        assertFalse(policy.decide(10_000, "excel", true).asynchronous());
                    }

                    @Test
                    void shouldRejectNonAdministrator() {
                        assertThrows(SecurityException.class,
                                () -> policy.decide(100, "csv", false));
                    }
                }
                """;
    }
}
