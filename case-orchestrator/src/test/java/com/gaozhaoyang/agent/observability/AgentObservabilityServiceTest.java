package com.gaozhaoyang.agent.observability;

import com.gaozhaoyang.agent.tool.ToolGovernanceService;
import com.gaozhaoyang.agent.workflow.AgentTraceSpan;
import com.gaozhaoyang.agent.workflow.InMemoryWorkflowRepository;
import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentObservabilityServiceTest {

    @Test
    void shouldBuildCorrelatedEndToEndTraceFromDurableState() {
        Instant startedAt = Instant.now().minusSeconds(2);
        WorkflowState workflow = WorkflowState.start("订单列表增加导出功能")
                .addTraceSpan(AgentTraceSpan.success(
                        "workflow-1", "agent.requirement-analysis", startedAt,
                        Map.of("mode", "MODEL")))
                .addTraceSpan(AgentTraceSpan.success(
                        "workflow-1", "rag.agentic-evidence-research", startedAt,
                        Map.of("evidence.count", "3")));
        workflow = withWorkflowId(workflow, "workflow-1");
        InMemoryWorkflowRepository workflows = new InMemoryWorkflowRepository();
        workflows.save(workflow);

        AgentTraceContext context = new AgentTraceContext();
        ToolGovernanceService tools = new ToolGovernanceService(context);
        context.withinTrace("workflow-1", () -> tools.executeReadOnly(
                ToolGovernanceService.DATABASE_METADATA_TOOL,
                "SPRING_AI", "订单导出", () -> "orders(id)"));

        AgentObservabilityService service = new AgentObservabilityService(
                workflows, tools);
        UnifiedAgentTraceReport report = service.trace("workflow-1");

        assertThat(report.status()).isEqualTo("RUNNING");
        assertThat(report.codingTaskId()).isEmpty();
        assertThat(report.spans()).extracting(UnifiedAgentSpan::category)
                .contains("AGENT", "RAG", "TOOL");
        assertThat(report.summary().toolCalls()).isEqualTo(1);
        assertThat(report.summary().agentLoopSteps()).isZero();
        assertThat(report.summary().buildExecutions()).isZero();
        assertThat(report.summary().failedSpanCount()).isZero();
        assertThat(report.summary().tokenUsageAvailable()).isFalse();
        assertThat(report.summary().tokenUsageNote()).contains("不使用字符数伪造 Token");
        assertThat(report.spans()).allSatisfy(span ->
                assertThat(span.traceId()).isEqualTo("workflow-1"));
    }

    @Test
    void shouldRejectUnknownWorkflow() {
        AgentObservabilityService service = new AgentObservabilityService(
                new InMemoryWorkflowRepository(),
                new ToolGovernanceService());

        assertThatThrownBy(() -> service.trace("missing"))
                .hasMessageContaining("missing");
    }

    private WorkflowState withWorkflowId(WorkflowState state, String workflowId) {
        List<AgentTraceSpan> spans = state.traceSpans().stream()
                .map(span -> new AgentTraceSpan(
                        span.spanId(), workflowId, span.parentSpanId(), span.operation(),
                        span.status(), span.startedAt(), span.endedAt(), span.durationMs(),
                        span.attributes()))
                .toList();
        return new WorkflowState(
                workflowId, state.requirement(), state.clarifications(), state.stage(),
                state.requirementCard(), state.knowledgeResults(), state.evidenceResearch(),
                state.technicalSolution(), state.solutionGrounding(), state.solutionCritique(),
                state.solutionFeedbacks(), state.events(), state.revision(), state.createdAt(),
                state.updatedAt(), spans, state.failureMessage()
        );
    }
}
