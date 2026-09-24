package com.gaozhaoyang.agent.observability;

import com.gaozhaoyang.agent.tool.ToolAuditRecord;
import com.gaozhaoyang.agent.tool.ToolGovernanceService;
import com.gaozhaoyang.agent.workflow.AgentTraceSpan;
import com.gaozhaoyang.agent.workflow.WorkflowNotFoundException;
import com.gaozhaoyang.agent.workflow.WorkflowRepository;
import com.gaozhaoyang.agent.workflow.WorkflowStage;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将旧版工作流和工具审计投影为一条 Trace；Coding Agent 已迁出主应用。 */
@Service
public class AgentObservabilityService {

    private final WorkflowRepository workflowRepository;
    private final ToolGovernanceService toolGovernanceService;

    public AgentObservabilityService(
            WorkflowRepository workflowRepository,
            ToolGovernanceService toolGovernanceService
    ) {
        this.workflowRepository = workflowRepository;
        this.toolGovernanceService = toolGovernanceService;
    }

    public UnifiedAgentTraceReport trace(String workflowId) {
        WorkflowState workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        String rootSpanId = "workflow:" + workflowId;
        List<UnifiedAgentSpan> spans = new ArrayList<>();

        workflow.traceSpans().stream()
                .map(span -> workflowSpan(span, rootSpanId))
                .forEach(spans::add);
        toolGovernanceService.recentForTrace(workflowId, 100).stream()
                .map(audit -> toolSpan(audit, rootSpanId))
                .forEach(spans::add);
        spans.sort(Comparator.comparing(UnifiedAgentSpan::startedAt)
                .thenComparing(UnifiedAgentSpan::spanId));

        Instant updatedAt = workflow.updatedAt();
        long totalDuration = Math.max(
                Duration.between(workflow.createdAt(), updatedAt).toMillis(), 0);
        AgentRunSummary summary = summarize(spans, totalDuration);
        return new UnifiedAgentTraceReport(
                workflowId,
                workflowId,
                "",
                overallStatus(workflow),
                workflow.createdAt(),
                updatedAt,
                summary,
                List.copyOf(spans)
        );
    }

    private UnifiedAgentSpan workflowSpan(AgentTraceSpan span, String rootSpanId) {
        return new UnifiedAgentSpan(
                span.spanId(), span.traceId(), rootSpanId,
                category(span.operation()), span.operation(), span.status(),
                span.startedAt(), span.endedAt(), span.durationMs(), span.attributes()
        );
    }

    private UnifiedAgentSpan toolSpan(ToolAuditRecord audit, String rootSpanId) {
        Instant startedAt = audit.occurredAt().minusMillis(audit.durationMs());
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("tool.channel", audit.channel());
        attributes.put("input.chars", String.valueOf(audit.inputChars()));
        attributes.put("output.chars", String.valueOf(audit.outputChars()));
        if (!audit.errorType().isBlank()) {
            attributes.put("error.type", audit.errorType());
        }
        return new UnifiedAgentSpan(
                audit.auditId(), audit.traceId(), rootSpanId,
                "TOOL", "tool." + audit.toolName(), audit.status(),
                startedAt, audit.occurredAt(), audit.durationMs(), attributes
        );
    }

    private AgentRunSummary summarize(List<UnifiedAgentSpan> spans, long totalDuration) {
        int failed = (int) spans.stream().filter(span ->
                "ERROR".equals(span.status()) || "DENIED".equals(span.status())).count();
        return new AgentRunSummary(
                spans.size(),
                failed,
                countCategory(spans, "AGENT"),
                countCategory(spans, "RAG"),
                countCategory(spans, "TOOL"),
                countCategory(spans, "AGENT_LOOP"),
                countCategory(spans, "SANDBOX"),
                totalDuration,
                false,
                0,
                0,
                "当前 DeepSeek 结构化调用链未稳定返回 Token Usage，因此明确标记为不可用，不使用字符数伪造 Token。"
        );
    }

    private int countCategory(List<UnifiedAgentSpan> spans, String category) {
        return (int) spans.stream().filter(span -> category.equals(span.category())).count();
    }

    private String category(String operation) {
        if (operation.startsWith("rag.")) {
            return "RAG";
        }
        if (operation.startsWith("agent.")) {
            return "AGENT";
        }
        return "WORKFLOW";
    }

    private String overallStatus(WorkflowState workflow) {
        if (workflow.stage() == WorkflowStage.FAILED) {
            return "ERROR";
        }
        return switch (workflow.stage()) {
            case COMPLETED -> "SUCCESS";
            case WAITING_CLARIFICATION, WAITING_APPROVAL -> "WAITING";
            default -> "RUNNING";
        };
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "…";
    }
}
