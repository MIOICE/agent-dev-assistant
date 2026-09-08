package com.gaozhaoyang.agent.tool;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolGovernanceController {

    private final ToolGovernanceService governanceService;

    public ToolGovernanceController(ToolGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("/status")
    public ToolGovernanceStatus status() {
        return new ToolGovernanceStatus(
                "MCP Streamable HTTP",
                "/api/mcp",
                "默认仅监听127.0.0.1",
                governanceService.allowedTools(),
                governanceService.auditCount(),
                List.of(
                        "仅白名单工具可调用",
                        "查询参数不能为空且最多500字",
                        "工具只读，不执行SQL写操作",
                        "不记录完整查询内容和返回内容",
                        "单轮最多8次工具调用，单工具最多4次"
                )
        );
    }

    @GetMapping("/audits")
    public List<ToolAuditRecord> audits(@RequestParam(defaultValue = "20") int limit) {
        return governanceService.recent(limit);
    }
}
