package com.gaozhaoyang.agent.tool;

import com.gaozhaoyang.agent.observability.AgentTraceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

@Service
public class ToolGovernanceService {

    public static final String BUSINESS_DOCUMENT_TOOL = "search_business_document";
    public static final String DATABASE_METADATA_TOOL = "query_database_metadata";
    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_AUDIT_RECORDS = 500;
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            BUSINESS_DOCUMENT_TOOL,
            DATABASE_METADATA_TOOL
    );

    private final ConcurrentLinkedDeque<ToolAuditRecord> auditRecords =
            new ConcurrentLinkedDeque<>();
    private final AgentTraceContext traceContext;

    public ToolGovernanceService() {
        this(new AgentTraceContext());
    }

    @Autowired
    public ToolGovernanceService(AgentTraceContext traceContext) {
        this.traceContext = traceContext;
    }

    public String executeReadOnly(
            String toolName,
            String channel,
            String query,
            Supplier<String> action
    ) {
        Instant startedAt = Instant.now();
        if (!ALLOWED_TOOLS.contains(toolName)) {
            record(toolName, channel, "DENIED", safeLength(query),
                    0, startedAt, "ToolNotAllowed");
            throw new IllegalArgumentException("工具不在白名单中：" + toolName);
        }
        String normalizedQuery;
        try {
            normalizedQuery = validateQuery(query);
        } catch (IllegalArgumentException exception) {
            record(toolName, channel, "DENIED", safeLength(query),
                    0, startedAt, exception.getClass().getSimpleName());
            throw exception;
        }
        try {
            String result = action.get();
            record(toolName, channel, "SUCCESS", normalizedQuery.length(),
                    result == null ? 0 : result.length(), startedAt, "");
            return result;
        } catch (RuntimeException exception) {
            record(toolName, channel, "ERROR", normalizedQuery.length(),
                    0, startedAt, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public List<ToolAuditRecord> recent(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return auditRecords.stream().limit(safeLimit).toList();
    }

    public int auditCount() {
        return auditRecords.size();
    }

    public List<ToolAuditRecord> recentForTrace(String traceId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return auditRecords.stream()
                .filter(record -> traceId.equals(record.traceId()))
                .limit(safeLimit)
                .toList();
    }

    public List<String> allowedTools() {
        return ALLOWED_TOOLS.stream().sorted().toList();
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("工具查询内容不能为空");
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("工具查询内容不能超过500字");
        }
        return normalized;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private void record(
            String toolName,
            String channel,
            String status,
            int inputChars,
            int outputChars,
            Instant startedAt,
            String errorType
    ) {
        Instant endedAt = Instant.now();
        auditRecords.addFirst(new ToolAuditRecord(
                UUID.randomUUID().toString(),
                traceContext.currentTraceId().orElse(""),
                toolName,
                channel == null || channel.isBlank() ? "UNKNOWN" : channel,
                status,
                inputChars,
                outputChars,
                Math.max(Duration.between(startedAt, endedAt).toMillis(), 0),
                endedAt,
                errorType
        ));
        while (auditRecords.size() > MAX_AUDIT_RECORDS) {
            auditRecords.pollLast();
        }
    }
}
