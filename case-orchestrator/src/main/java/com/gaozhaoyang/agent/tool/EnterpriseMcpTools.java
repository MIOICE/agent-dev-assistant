package com.gaozhaoyang.agent.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EnterpriseMcpTools {

    private final BusinessDocumentTools businessDocumentTools;
    private final DatabaseMetadataTools databaseMetadataTools;
    private final ToolGovernanceService governanceService;

    public EnterpriseMcpTools(
            BusinessDocumentTools businessDocumentTools,
            DatabaseMetadataTools databaseMetadataTools,
            ToolGovernanceService governanceService
    ) {
        this.businessDocumentTools = businessDocumentTools;
        this.databaseMetadataTools = databaseMetadataTools;
        this.governanceService = governanceService;
    }

    @McpTool(
            name = ToolGovernanceService.BUSINESS_DOCUMENT_TOOL,
            title = "检索企业业务规范",
            description = "只读检索企业业务规范，返回匹配片段、来源ID和相似度；不会修改文档或业务数据。",
            annotations = @McpTool.McpAnnotations(
                    title = "检索企业业务规范",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false
            )
    )
    public String searchBusinessDocument(
            @McpToolParam(description = "业务需求或检索关键词，最多500字", required = true)
            String query
    ) {
        return governanceService.executeReadOnly(
                ToolGovernanceService.BUSINESS_DOCUMENT_TOOL,
                "MCP",
                query,
                () -> businessDocumentTools.searchBusinessDocumentRaw(query.trim())
        );
    }

    @McpTool(
            name = ToolGovernanceService.DATABASE_METADATA_TOOL,
            title = "查询数据库元数据",
            description = "只读查询演示数据库的表、字段和索引元数据；不读取行数据，不执行SQL写操作。",
            annotations = @McpTool.McpAnnotations(
                    title = "查询数据库元数据",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false
            )
    )
    public String queryDatabaseMetadata(
            @McpToolParam(description = "业务需求或表结构关键词，最多500字", required = true)
            String query
    ) {
        return governanceService.executeReadOnly(
                ToolGovernanceService.DATABASE_METADATA_TOOL,
                "MCP",
                query,
                () -> databaseMetadataTools.queryDatabaseMetadataRaw(query.trim())
        );
    }
}
