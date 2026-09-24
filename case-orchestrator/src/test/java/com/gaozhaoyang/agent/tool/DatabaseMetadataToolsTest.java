package com.gaozhaoyang.agent.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMetadataToolsTest {

    private final DatabaseMetadataTools tools = new DatabaseMetadataTools();

    @Test
    void shouldFindOrderCustomerAndAuditTablesForExportRequirement() {
        String result = tools.queryDatabaseMetadata(
                "订单列表根据客户名称筛选并支持导出"
        );

        assertThat(result)
                .contains("DB-TABLE-orders")
                .contains("DB-TABLE-customers")
                .contains("DB-TABLE-operation_log");
    }

    @Test
    void shouldReturnFieldsAndIndexes() {
        String result = tools.queryDatabaseMetadata("批量修改订单状态");

        assertThat(result)
                .contains("status VARCHAR(32) 订单状态")
                .contains("idx_customer_status(customer_id, status) 联合索引");
    }

    @Test
    void shouldReturnMessageWhenNoTableMatches() {
        String result = tools.queryDatabaseMetadata("调整页面主题颜色");

        assertThat(result).isEqualTo("未找到与当前需求相关的数据库元数据");
    }

    @Test
    void shouldRejectBlankQuery() {
        String result = tools.queryDatabaseMetadata("   ");

        assertThat(result).isEqualTo("元数据查询关键词不能为空");
    }
}
