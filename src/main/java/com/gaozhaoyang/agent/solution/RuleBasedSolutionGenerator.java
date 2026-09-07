package com.gaozhaoyang.agent.solution;

import com.gaozhaoyang.agent.knowledge.KnowledgeSearchResult;
import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
@ConditionalOnProperty(
        name = "app.ai.mode",
        havingValue = "mock",
        matchIfMissing = true
)
public class RuleBasedSolutionGenerator implements SolutionGenerator {

    @Override
    public TechnicalSolution generate(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults
    ) {
        List<String> sources = knowledgeResults.stream()
                .map(result -> result.sourceId() + "｜" + result.title())
                .distinct()
                .toList();

        return new TechnicalSolution(
                "根据需求卡片和检索到的业务规范生成实施方案，所有具体表名、字段名和权限编码需在开发前确认。",
                List.of(
                        "在现有业务模块中增加对应的应用服务，负责参数校验、权限检查和流程编排",
                        "将耗时任务与同步请求解耦，并向调用方返回明确的任务状态"
                ),
                List.of(
                        "开发前通过只读元数据查询确认相关表、字段、索引和数据规模",
                        "不直接假设数据库结构；如需新增字段或索引，必须经过评审"
                ),
                List.of(
                        "定义请求参数、响应结构和统一异常码",
                        "对重复提交、无权限访问和大数据量请求返回可识别的处理结果"
                ),
                List.of(
                        "执行角色权限与数据范围校验",
                        "记录操作人、请求参数、执行时间和结果，敏感信息不得写入普通日志"
                ),
                List.of(
                        "根据实际数据量决定同步或异步执行方式",
                        "限制单次处理规模，并通过分页或流式处理避免一次性加载大量数据"
                ),
                List.of(
                        "覆盖正常流程、参数异常、无权限、重复提交和执行失败场景",
                        "验证处理结果与页面筛选条件、业务规则及历史数据保持一致"
                ),
                List.of(
                        "保留原有流程开关，异常时可切回旧实现",
                        "数据库变更必须提供可执行的回滚脚本并先完成备份验证"
                ),
                sources.isEmpty()
                        ? List.of("当前没有命中业务资料，方案进入开发前必须补充人工确认")
                        : List.of("方案参考资料：" + String.join("、", sources))
        );
    }

    @Override
    public TechnicalSolution regenerate(
            RequirementCard requirementCard,
            List<KnowledgeSearchResult> knowledgeResults,
            List<String> reviewerFeedback
    ) {
        TechnicalSolution original = generate(requirementCard, knowledgeResults);
        List<String> assumptions = new ArrayList<>(original.assumptions());
        reviewerFeedback.forEach(feedback ->
                assumptions.add("已根据人工审批意见调整方案：" + feedback));
        return new TechnicalSolution(
                original.summary(),
                original.backendChanges(),
                original.databaseChanges(),
                original.apiDesign(),
                original.securityControls(),
                original.performanceStrategy(),
                original.testPlan(),
                original.rollbackPlan(),
                List.copyOf(assumptions)
        );
    }
}
