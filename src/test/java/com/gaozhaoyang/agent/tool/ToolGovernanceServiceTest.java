package com.gaozhaoyang.agent.tool;

import com.gaozhaoyang.agent.observability.AgentTraceContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolGovernanceServiceTest {

    private final ToolGovernanceService governanceService = new ToolGovernanceService();

    @Test
    void shouldExecuteWhitelistedToolAndRecordSuccessWithoutContent() {
        String result = governanceService.executeReadOnly(
                ToolGovernanceService.DATABASE_METADATA_TOOL,
                "MCP",
                " 订单导出 ",
                () -> "orders(id, order_no)"
        );

        assertThat(result).isEqualTo("orders(id, order_no)");
        assertThat(governanceService.auditCount()).isEqualTo(1);
        assertThat(governanceService.recent(1).getFirst())
                .satisfies(audit -> {
                    assertThat(audit.toolName())
                            .isEqualTo(ToolGovernanceService.DATABASE_METADATA_TOOL);
                    assertThat(audit.channel()).isEqualTo("MCP");
                    assertThat(audit.status()).isEqualTo("SUCCESS");
                    assertThat(audit.inputChars()).isEqualTo(4);
                    assertThat(audit.outputChars()).isEqualTo(20);
                    assertThat(audit.errorType()).isEmpty();
                });
    }

    @Test
    void shouldDenyToolOutsideWhitelistAndRecordAttempt() {
        assertThatThrownBy(() -> governanceService.executeReadOnly(
                "execute_sql",
                "MCP",
                "delete from orders",
                () -> "never executed"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工具不在白名单中");

        ToolAuditRecord audit = governanceService.recent(1).getFirst();
        assertThat(audit.status()).isEqualTo("DENIED");
        assertThat(audit.errorType()).isEqualTo("ToolNotAllowed");
    }

    @Test
    void shouldRejectInvalidArgumentsAndRecordAttempt() {
        assertThatThrownBy(() -> governanceService.executeReadOnly(
                ToolGovernanceService.BUSINESS_DOCUMENT_TOOL,
                "MCP",
                " ",
                () -> "never executed"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");

        assertThat(governanceService.recent(1).getFirst().status())
                .isEqualTo("DENIED");
    }

    @Test
    void shouldRecordToolExecutionFailure() {
        assertThatThrownBy(() -> governanceService.executeReadOnly(
                ToolGovernanceService.BUSINESS_DOCUMENT_TOOL,
                "MCP",
                "订单规范",
                () -> {
                    throw new IllegalStateException("retriever unavailable");
                }
        )).isInstanceOf(IllegalStateException.class);

        ToolAuditRecord audit = governanceService.recent(1).getFirst();
        assertThat(audit.status()).isEqualTo("ERROR");
        assertThat(audit.errorType()).isEqualTo("IllegalStateException");
    }

    @Test
    void shouldCorrelateToolAuditWithoutRecordingSensitiveContent() {
        AgentTraceContext context = new AgentTraceContext();
        ToolGovernanceService service = new ToolGovernanceService(context);

        context.withinTrace("workflow-42", () -> service.executeReadOnly(
                ToolGovernanceService.DATABASE_METADATA_TOOL,
                "SPRING_AI",
                "客户手机号",
                () -> "contact_phone"
        ));

        ToolAuditRecord audit = service.recentForTrace("workflow-42", 10).getFirst();
        assertThat(audit.traceId()).isEqualTo("workflow-42");
        assertThat(audit.inputChars()).isEqualTo(5);
        assertThat(audit.outputChars()).isEqualTo(13);
        assertThat(audit.toString()).doesNotContain("客户手机号", "contact_phone");
    }
}
