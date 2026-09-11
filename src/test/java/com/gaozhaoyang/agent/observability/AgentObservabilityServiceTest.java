package com.gaozhaoyang.agent.observability;

import com.gaozhaoyang.agent.coding.AgentLoopAction;
import com.gaozhaoyang.agent.coding.AgentLoopState;
import com.gaozhaoyang.agent.coding.AgentLoopStopCode;
import com.gaozhaoyang.agent.coding.AutonomyBudget;
import com.gaozhaoyang.agent.coding.BuildAttempt;
import com.gaozhaoyang.agent.coding.BuildVerification;
import com.gaozhaoyang.agent.coding.CodingTask;
import com.gaozhaoyang.agent.coding.CodingTaskStage;
import com.gaozhaoyang.agent.coding.InMemoryCodingTaskRepository;
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

        BuildVerification verification = new BuildVerification(
                false, "mvn test", 1, 25, "compile failed");
        AgentLoopState loop = AgentLoopState.initial(8)
                .record(AgentLoopAction.GENERATE_PATCH, "待生成", "生成候选", "已生成", "f1", true)
                .record(AgentLoopAction.RUN_SANDBOX_TEST, "待验证", "执行测试", "编译失败", "f2", false)
                .stop(AgentLoopStopCode.FAILURE, "构建失败");
        Instant now = Instant.now();
        CodingTask task = new CodingTask(
                "task-1", "workflow-1", workflow, CodingTaskStage.FAILED,
                "生成订单导出", AutonomyBudget.safeDefault(),
                2, 800, 1, 25, 0, List.of("java-code-generation"),
                List.of(), verification, List.of(new BuildAttempt(1, verification, now)),
                List.of(), loop, "sandbox-1", "", "构建失败", startedAt, now
        );
        InMemoryCodingTaskRepository codingTasks = new InMemoryCodingTaskRepository();
        codingTasks.save(task);

        AgentObservabilityService service = new AgentObservabilityService(
                workflows, codingTasks, tools);
        UnifiedAgentTraceReport report = service.trace("workflow-1");

        assertThat(report.status()).isEqualTo("ERROR");
        assertThat(report.codingTaskId()).isEqualTo("task-1");
        assertThat(report.spans()).extracting(UnifiedAgentSpan::category)
                .contains("AGENT", "RAG", "TOOL", "CODING_AGENT", "AGENT_LOOP", "SANDBOX");
        assertThat(report.summary().toolCalls()).isEqualTo(1);
        assertThat(report.summary().agentLoopSteps()).isEqualTo(2);
        assertThat(report.summary().buildExecutions()).isEqualTo(1);
        assertThat(report.summary().failedSpanCount()).isEqualTo(2);
        assertThat(report.summary().tokenUsageAvailable()).isFalse();
        assertThat(report.summary().tokenUsageNote()).contains("不使用字符数伪造 Token");
        assertThat(report.spans()).allSatisfy(span ->
                assertThat(span.traceId()).isEqualTo("workflow-1"));
    }

    @Test
    void shouldRejectUnknownWorkflow() {
        AgentObservabilityService service = new AgentObservabilityService(
                new InMemoryWorkflowRepository(),
                new InMemoryCodingTaskRepository(),
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
