package com.gaozhaoyang.agent.tool;

import java.util.List;

public record ToolGovernanceStatus(
        String protocol,
        String endpoint,
        String exposure,
        List<String> allowedTools,
        int auditCount,
        List<String> policies
) {
}
