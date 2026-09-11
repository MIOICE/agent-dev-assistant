package com.gaozhaoyang.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DatabaseMetadataTools {

    private final ToolGovernanceService governanceService;

    private static final List<TableMetadata> TABLES = List.of(
            new TableMetadata(
                    "DB-TABLE-orders",
                    "orders",
                    "订单主表",
                    List.of("订单", "订单状态", "导出", "筛选", "删除", "批量修改"),
                    List.of(
                            "id BIGINT 主键",
                            "order_no VARCHAR(64) 订单编号",
                            "customer_id BIGINT 客户ID",
                            "status VARCHAR(32) 订单状态",
                            "total_amount DECIMAL(18,2) 订单金额",
                            "deleted TINYINT 逻辑删除标记",
                            "created_at DATETIME 创建时间"
                    ),
                    List.of(
                            "uk_order_no(order_no) 唯一索引",
                            "idx_customer_status(customer_id, status) 联合索引",
                            "idx_created_at(created_at) 普通索引"
                    )
            ),
            new TableMetadata(
                    "DB-TABLE-customers",
                    "customers",
                    "客户主表",
                    List.of("客户", "客户名称", "敏感数据", "订单"),
                    List.of(
                            "id BIGINT 主键",
                            "customer_code VARCHAR(64) 客户编码",
                            "name VARCHAR(128) 客户名称",
                            "contact_phone VARCHAR(32) 联系电话（敏感字段）",
                            "data_scope VARCHAR(32) 数据权限范围"
                    ),
                    List.of(
                            "uk_customer_code(customer_code) 唯一索引",
                            "idx_customer_name(name) 普通索引"
                    )
            ),
            new TableMetadata(
                    "DB-TABLE-operation_log",
                    "operation_log",
                    "操作审计日志表",
                    List.of("日志", "审计", "导出", "删除", "批量修改"),
                    List.of(
                            "id BIGINT 主键",
                            "operator_id BIGINT 操作人ID",
                            "action VARCHAR(64) 操作类型",
                            "target_type VARCHAR(64) 操作对象类型",
                            "target_id VARCHAR(128) 操作对象标识",
                            "result VARCHAR(32) 操作结果",
                            "created_at DATETIME 操作时间"
                    ),
                    List.of(
                            "idx_operator_time(operator_id, created_at) 联合索引",
                            "idx_target(target_type, target_id) 联合索引"
                    )
            )
    );

    public DatabaseMetadataTools() {
        this(new ToolGovernanceService());
    }

    @Autowired
    public DatabaseMetadataTools(ToolGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Tool(description = "根据业务需求查询数据库表、字段和索引元数据。涉及字段筛选、数据导出、批量修改、删除或表关系分析时应调用此工具。工具只返回结构信息，不读取或修改真实业务数据。")
    public String queryDatabaseMetadata(
            @ToolParam(description = "用于查找相关表结构的业务关键词或完整需求描述")
            String query) {
        if (query == null || query.isBlank()) {
            return "元数据查询关键词不能为空";
        }

        return governanceService.executeReadOnly(
                ToolGovernanceService.DATABASE_METADATA_TOOL,
                "SPRING_AI",
                query,
                () -> queryDatabaseMetadataRaw(query.trim())
        );
    }

    String queryDatabaseMetadataRaw(String query) {

        List<TableMetadata> matches = TABLES.stream()
                .filter(table -> table.keywords().stream()
                        .anyMatch(query::contains))
                .toList();

        if (matches.isEmpty()) {
            return "未找到与当前需求相关的数据库元数据";
        }

        return matches.stream()
                .map(this::formatTable)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatTable(TableMetadata table) {
        String columns = String.join("；", table.columns());
        String indexes = String.join("；", table.indexes());
        return "[%s｜%s(%s)]%n字段：%s%n索引：%s".formatted(
                table.id(),
                table.businessName(),
                table.tableName(),
                columns,
                indexes
        );
    }

    private record TableMetadata(
            String id,
            String tableName,
            String businessName,
            List<String> keywords,
            List<String> columns,
            List<String> indexes
    ) {
    }
}
