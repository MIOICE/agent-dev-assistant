package com.gaozhaoyang.agent.workflow;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearcher;
import com.gaozhaoyang.agent.requirement.RequirementAnalyzer;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import com.gaozhaoyang.agent.solution.SolutionGenerator;
import com.gaozhaoyang.agent.solution.TechnicalSolution;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementWorkflowServiceTest {

    @Test
    void shouldStopBeforeRetrievalWhenRequirementNeedsClarification() {
        RequirementCard incompleteCard = new RequirementCard(
                "增加导出功能",
                "增加导出功能",
                List.of(),
                List.of("系统能够触发导出操作"),
                List.of("需要导出哪个业务对象？", "导出文件格式是什么？"),
                "P2",
                List.of("数据范围不明确可能导致越权导出"),
                List.of(),
                false
        );

        RequirementAnalyzer analyzer = requirement -> incompleteCard;
        KnowledgeSearcher searcher = query -> {
            throw new AssertionError("信息不完整时不应该执行知识检索");
        };
        SolutionGenerator solutionGenerator = (requirementCard, knowledgeResults) -> {
            throw new AssertionError("信息不完整时不应该生成技术方案");
        };
        WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();

        RequirementWorkflowService service = new RequirementWorkflowService(
                analyzer,
                searcher,
                solutionGenerator,
                workflowRepository
        );

        WorkflowState state = service.start("增加导出功能");

        assertThat(state.stage()).isEqualTo(WorkflowStage.WAITING_CLARIFICATION);
        assertThat(state.requirementCard()).isEqualTo(incompleteCard);
        assertThat(state.knowledgeResults()).isEmpty();
        assertThat(state.technicalSolution()).isNull();
    }

    @Test
    void shouldGenerateSolutionAndWaitForApproval() {
        RequirementCard card = new RequirementCard(
                "订单列表增加全量导出",
                "订单列表需要支持全量导出",
                List.of("订单管理"),
                List.of("能够导出订单数据"),
                List.of(),
                "P1",
                List.of("大数据量导出可能造成数据库压力"),
                List.of(),
                true
        );

        RequirementAnalyzer analyzer = requirement -> card;
        KnowledgeSearcher searcher = query -> List.of(
                Document.builder()
                        .id("DOC-EXPORT-001#chunk-1")
                        .text("大数据量导出应采用异步任务，并限制单次导出规模。")
                        .metadata("sourceId", "DOC-EXPORT-001")
                        .metadata("title", "数据导出规范")
                        .metadata("chunkIndex", 1)
                        .metadata("keywords", List.of("导出", "异步任务"))
                        .build()
        );
        TechnicalSolution technicalSolution = new TechnicalSolution(
                "使用异步任务完成订单导出",
                List.of("增加导出任务服务"),
                List.of("确认订单表索引"),
                List.of("定义导出任务接口"),
                List.of("校验导出权限"),
                List.of("采用分页读取"),
                List.of("验证大数据量导出"),
                List.of("保留原有导出流程"),
                List.of("导出字段范围待确认")
        );
        SolutionGenerator solutionGenerator = (requirementCard, knowledgeResults) ->
                technicalSolution;
        WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();

        RequirementWorkflowService service = new RequirementWorkflowService(
                analyzer,
                searcher,
                solutionGenerator,
                workflowRepository
        );

        WorkflowState state = service.start("订单列表增加全量导出功能");

        assertThat(state.stage()).isEqualTo(WorkflowStage.WAITING_APPROVAL);
        assertThat(state.requirementCard()).isEqualTo(card);
        assertThat(state.knowledgeResults()).hasSize(1);
        assertThat(state.knowledgeResults().getFirst().sourceId())
                .isEqualTo("DOC-EXPORT-001");
        assertThat(state.technicalSolution()).isEqualTo(technicalSolution);
    }

    @Test
    void shouldResumeSameWorkflowAfterClarification() {
        RequirementCard incompleteCard = new RequirementCard(
                "增加导出功能",
                "增加导出功能",
                List.of(),
                List.of("能够触发导出"),
                List.of("需要导出哪个业务对象？"),
                "P2",
                List.of(),
                List.of(),
                false
        );
        RequirementCard completeCard = new RequirementCard(
                "订单列表增加全量导出",
                "订单列表增加全量导出，文件格式为CSV",
                List.of("订单管理"),
                List.of("能够导出当前筛选范围内的订单CSV文件"),
                List.of(),
                "P2",
                List.of("大数据量导出可能影响性能"),
                List.of(),
                true
        );

        RequirementAnalyzer analyzer = requirement ->
                requirement.contains("用户补充信息")
                        ? completeCard
                        : incompleteCard;
        AtomicReference<String> searchQuery = new AtomicReference<>();
        KnowledgeSearcher searcher = query -> {
            searchQuery.set(query);
            return List.of(
                Document.builder()
                        .id("DOC-EXPORT-001#chunk-1")
                        .text("订单导出应校验数据权限。")
                        .metadata("sourceId", "DOC-EXPORT-001")
                        .metadata("title", "数据导出规范")
                        .metadata("chunkIndex", 1)
                        .metadata("keywords", List.of("订单", "导出"))
                        .build()
            );
        };
        TechnicalSolution solution = new TechnicalSolution(
                "生成订单CSV导出任务",
                List.of("增加导出任务服务"),
                List.of("确认查询索引"),
                List.of("增加导出任务接口"),
                List.of("校验数据权限"),
                List.of("分页查询"),
                List.of("验证CSV内容"),
                List.of("关闭新功能开关"),
                List.of()
        );
        SolutionGenerator solutionGenerator = (card, knowledge) -> solution;
        WorkflowRepository workflowRepository = new InMemoryWorkflowRepository();
        RequirementWorkflowService service = new RequirementWorkflowService(
                analyzer,
                searcher,
                solutionGenerator,
                workflowRepository
        );

        WorkflowState waitingState = service.start("增加导出功能");
        WorkflowState resumedState = service.clarify(
                waitingState.workflowId(),
                "导出订单列表当前筛选结果，文件格式为CSV"
        );

        assertThat(resumedState.workflowId()).isEqualTo(waitingState.workflowId());
        assertThat(resumedState.clarifications())
                .containsExactly("导出订单列表当前筛选结果，文件格式为CSV");
        assertThat(searchQuery.get()).isEqualTo("增加导出功能 订单管理");
        assertThat(resumedState.stage()).isEqualTo(WorkflowStage.WAITING_APPROVAL);
        assertThat(resumedState.technicalSolution()).isEqualTo(solution);
        assertThat(service.get(resumedState.workflowId())).isEqualTo(resumedState);
    }

    @Test
    void shouldCompleteWorkflowOnlyAfterHumanApproval() {
        RequirementWorkflowService service = readyWorkflowService(
                (card, knowledge) -> sampleSolution()
        );
        WorkflowState waiting = service.start("订单列表增加全量导出功能");

        WorkflowState completed = service.approve(waiting.workflowId(), "安全与回滚方案已确认");

        assertThat(completed.stage()).isEqualTo(WorkflowStage.COMPLETED);
        assertThat(completed.events().getLast().type()).isEqualTo(WorkflowEventType.APPROVED);
        assertThat(completed.events().getLast().message()).contains("安全与回滚方案已确认");
        assertThat(completed.revision()).isGreaterThan(waiting.revision());
    }

    @Test
    void shouldRegenerateSolutionWithReviewerFeedback() {
        AtomicReference<List<String>> receivedFeedback = new AtomicReference<>();
        SolutionGenerator generator = new SolutionGenerator() {
            @Override
            public TechnicalSolution generate(
                    RequirementCard requirementCard,
                    List<com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult> knowledgeResults
            ) {
                return sampleSolution();
            }

            @Override
            public TechnicalSolution regenerate(
                    RequirementCard requirementCard,
                    List<com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult> knowledgeResults,
                    List<String> reviewerFeedback
            ) {
                receivedFeedback.set(reviewerFeedback);
                TechnicalSolution original = sampleSolution();
                return new TechnicalSolution(
                        "改为异步导出并增加任务进度查询",
                        original.backendChanges(), original.databaseChanges(), original.apiDesign(),
                        original.securityControls(), original.performanceStrategy(), original.testPlan(),
                        original.rollbackPlan(), original.assumptions()
                );
            }
        };
        RequirementWorkflowService service = readyWorkflowService(generator);
        WorkflowState waiting = service.start("订单列表增加全量导出功能");

        WorkflowState regenerated = service.reject(
                waiting.workflowId(),
                "大数据量场景必须改成异步导出"
        );

        assertThat(receivedFeedback.get())
                .containsExactly("大数据量场景必须改成异步导出");
        assertThat(regenerated.stage()).isEqualTo(WorkflowStage.WAITING_APPROVAL);
        assertThat(regenerated.technicalSolution().summary()).contains("异步导出");
        assertThat(regenerated.events()).extracting(WorkflowEvent::type)
                .contains(WorkflowEventType.REJECTED, WorkflowEventType.SOLUTION_GENERATED);
    }

    @Test
    void shouldPersistFailureAndAllowRetryWithSameWorkflowId() {
        AtomicInteger calls = new AtomicInteger();
        RequirementAnalyzer analyzer = requirement -> {
            if (calls.getAndIncrement() == 0) {
                throw new RuntimeException("模拟模型超时");
            }
            return readyCard();
        };
        RequirementWorkflowService service = new RequirementWorkflowService(
                analyzer,
                query -> List.of(),
                (card, knowledge) -> sampleSolution(),
                new InMemoryWorkflowRepository()
        );

        WorkflowState failed = service.start("订单列表增加全量导出功能");
        WorkflowState retried = service.retry(failed.workflowId());

        assertThat(failed.stage()).isEqualTo(WorkflowStage.FAILED);
        assertThat(failed.failureMessage()).contains("需求分析");
        assertThat(retried.workflowId()).isEqualTo(failed.workflowId());
        assertThat(retried.stage()).isEqualTo(WorkflowStage.WAITING_APPROVAL);
        assertThat(retried.events()).extracting(WorkflowEvent::type)
                .contains(WorkflowEventType.FAILED, WorkflowEventType.RETRIED);
    }

    @Test
    void shouldListWorkflowSummaries() {
        RequirementWorkflowService service = readyWorkflowService(
                (card, knowledge) -> sampleSolution()
        );
        service.start("订单列表增加全量导出功能");
        service.start("订单状态批量修改功能");

        WorkflowPage page = service.list(WorkflowStage.WAITING_APPROVAL, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);
        assertThat(page.content()).allMatch(item ->
                item.stage() == WorkflowStage.WAITING_APPROVAL);
    }

    private RequirementWorkflowService readyWorkflowService(SolutionGenerator generator) {
        return new RequirementWorkflowService(
                requirement -> readyCard(),
                query -> List.of(),
                generator,
                new InMemoryWorkflowRepository()
        );
    }

    private RequirementCard readyCard() {
        return new RequirementCard(
                "订单列表增加全量导出",
                "订单列表需要支持全量导出",
                List.of("订单管理"),
                List.of("能够导出订单数据"),
                List.of(),
                "P1",
                List.of("大数据量导出可能造成数据库压力"),
                List.of(),
                true
        );
    }

    private TechnicalSolution sampleSolution() {
        return new TechnicalSolution(
                "使用异步任务完成订单导出",
                List.of("增加导出任务服务"),
                List.of("确认订单表索引"),
                List.of("定义导出任务接口"),
                List.of("校验导出权限"),
                List.of("采用分页读取"),
                List.of("验证大数据量导出"),
                List.of("保留原有导出流程"),
                List.of("导出字段范围待确认")
        );
    }
}
