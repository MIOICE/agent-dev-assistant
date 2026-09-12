package com.gaozhaoyang.agent.observability;

import com.gaozhaoyang.agent.coding.AgentLoopStep;
import com.gaozhaoyang.agent.coding.BuildAttempt;
import com.gaozhaoyang.agent.coding.CodingTask;
import com.gaozhaoyang.agent.coding.CodingTaskRepository;
import com.gaozhaoyang.agent.coding.CodingTaskStage;
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
import java.util.Optional;

/** 将工作流、工具审计和 Coding Agent Checkpoint 投影为一条端到端 Trace。 */
@Service
public class AgentObservabilityService {

    private final WorkflowRepository workflowRepository;
    private final CodingTaskRepository codingTaskRepository;
    private final ToolGovernanceService toolGovernanceService;

    public AgentObservabilityService(
            WorkflowRepository workflowRepository,
            CodingTaskRepository codingTaskRepository,
            ToolGovernanceService toolGovernanceService
    ) {
        this.workflowRepository = workflowRepository;
        this.codingTaskRepository = codingTaskRepository;
        this.toolGovernanceService = toolGovernanceService;
    }

    public UnifiedAgentTraceReport trace(String workflowId) {
        WorkflowState workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        Optional<CodingTask> codingTask = codingTaskRepository.findLatestByWorkflowId(workflowId);
        String rootSpanId = "workflow:" + workflowId;
        List<UnifiedAgentSpan> spans = new ArrayList<>();

        workflow.traceSpans().stream()
                .map(span -> workflowSpan(span, rootSpanId))
                .forEach(spans::add);
        toolGovernanceService.recentForTrace(workflowId, 100).stream()
                .map(audit -> toolSpan(audit, rootSpanId))
                .forEach(spans::add);
        codingTask.ifPresent(task -> addCodingSpans(task, rootSpanId, spans));
        spans.sort(Comparator.comparing(UnifiedAgentSpan::startedAt)
                .thenComparing(UnifiedAgentSpan::spanId));

        Instant updatedAt = codingTask.map(CodingTask::updatedAt)
                .filter(value -> value.isAfter(workflow.updatedAt()))
                .orElse(workflow.updatedAt());
        long totalDuration = Math.max(
                Duration.between(workflow.createdAt(), updatedAt).toMillis(), 0);
        AgentRunSummary summary = summarize(spans, totalDuration);
        return new UnifiedAgentTraceReport(
                workflowId,
                workflowId,
                codingTask.map(CodingTask::taskId).orElse(""),
                overallStatus(workflow, codingTask),
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

    private void addCodingSpans(
            CodingTask task,
            String rootSpanId,
            List<UnifiedAgentSpan> spans
    ) {
        String taskSpanId = "coding:" + task.taskId();
        spans.add(new UnifiedAgentSpan(
                taskSpanId, task.workflowId(), rootSpanId,
                "CODING_AGENT", "coding.task", codingStatus(task.stage()),
                task.createdAt(), task.updatedAt(),
                Math.max(Duration.between(task.createdAt(), task.updatedAt()).toMillis(), 0),
                Map.of(
                        "coding.task.id", task.taskId(),
                        "coding.stage", task.stage().name(),
                        "agent.stop.code", task.agentLoop().stopCode().name(),
                        "skills.count", String.valueOf(task.activatedSkills().size())
                )
        ));
        for (AgentLoopStep step : task.agentLoop().steps()) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("agent.step.sequence", String.valueOf(step.sequence()));
            attributes.put("agent.progress", String.valueOf(step.progressMade()));
            attributes.put("agent.outcome", bounded(step.outcome(), 240));
            attributes.put("timing.precision", "event_timestamp_only");
            spans.add(new UnifiedAgentSpan(
                    task.taskId() + ":step:" + step.sequence(),
                    task.workflowId(), taskSpanId,
                    "AGENT_LOOP", "agent.loop." + step.action().name().toLowerCase(),
                    step.progressMade() ? "SUCCESS" : "NO_PROGRESS",
                    step.occurredAt(), step.occurredAt(), 0, attributes
            ));
        }
        for (BuildAttempt attempt : task.buildAttempts()) {
            long duration = Math.max(attempt.verification().durationMs(), 0);
            Instant startedAt = attempt.completedAt().minusMillis(duration);
            spans.add(new UnifiedAgentSpan(
                    task.taskId() + ":build:" + attempt.attempt(),
                    task.workflowId(), taskSpanId,
                    "SANDBOX", "sandbox.build-and-test",
                    attempt.verification().passed() ? "SUCCESS" : "ERROR",
                    startedAt, attempt.completedAt(), duration,
                    Map.of(
                            "build.attempt", String.valueOf(attempt.attempt()),
                            "process.exit.code", String.valueOf(attempt.verification().exitCode()),
                            "build.command", bounded(attempt.verification().command(), 120)
                    )
            ));
        }
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

    private String overallStatus(WorkflowState workflow, Optional<CodingTask> codingTask) {
        if (workflow.stage() == WorkflowStage.FAILED
                || codingTask.map(CodingTask::stage).orElse(null) == CodingTaskStage.FAILED) {
            return "ERROR";
        }
        if (codingTask.isPresent()) {
            return codingStatus(codingTask.get().stage());
        }
        return switch (workflow.stage()) {
            case COMPLETED -> "SUCCESS";
            case WAITING_CLARIFICATION, WAITING_APPROVAL -> "WAITING";
            default -> "RUNNING";
        };
    }

    private String codingStatus(CodingTaskStage stage) {
        return switch (stage) {
            case FAILED -> "ERROR";
            case PUBLISHED -> "SUCCESS";
            case WAITING_APPROVAL -> "WAITING";
            case CANCELLED -> "CANCELLED";
            case TIMED_OUT -> "TIMED_OUT";
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
