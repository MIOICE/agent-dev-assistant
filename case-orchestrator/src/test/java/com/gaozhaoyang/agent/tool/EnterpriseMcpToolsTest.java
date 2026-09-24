package com.gaozhaoyang.agent.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseMcpToolsTest {

    @Test
    void shouldExposeDatabaseMetadataThroughGovernedMcpTool() {
        ToolGovernanceService governanceService = new ToolGovernanceService();
        EnterpriseMcpTools tools = new EnterpriseMcpTools(
                null,
                new DatabaseMetadataTools(),
                governanceService
        );

        String result = tools.queryDatabaseMetadata("订单导出");

        assertThat(result).contains("orders", "operation_log");
        assertThat(governanceService.recent(1).getFirst())
                .extracting(ToolAuditRecord::channel, ToolAuditRecord::status)
                .containsExactly("MCP", "SUCCESS");
    }
}
