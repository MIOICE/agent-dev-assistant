package com.gaozhaoyang.agent.knowledge;

import com.gaozhaoyang.agent.requirement.RequirementCard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class RuleBasedEvidencePlanner implements EvidencePlanner {

    @Override
    public EvidencePlan plan(String effectiveRequirement, RequirementCard card) {
        return plan(effectiveRequirement, card, "RULE_BASED");
    }

    EvidencePlan plan(
            String effectiveRequirement,
            RequirementCard card,
            String planningMode
    ) {
        String modules = String.join(" ", card.affectedModules());
        String base = (card.title() + " " + modules).trim();
        String fullContext = (effectiveRequirement + " " + card.title() + " "
                + modules + " " + String.join(" ", card.risks()))
                .toLowerCase(Locale.ROOT);
        List<EvidenceNeed> needs = new ArrayList<>();
        needs.add(new EvidenceNeed(
                "BUSINESS_RULES",
                base + " 业务规则 现有流程",
                "确认存量系统中的业务规则、页面入口和现有处理流程",
                true
        ));
        needs.add(new EvidenceNeed(
                "DATA_AND_API",
                base + " 数据字段 接口 依赖",
                "确认方案涉及的数据结构、接口和上下游依赖",
                true
        ));
        if (containsAny(fullContext, "权限", "敏感", "删除", "导出", "批量")) {
            needs.add(new EvidenceNeed(
                    "SECURITY",
                    base + " 权限 数据范围 审计 安全",
                    "确认功能权限、数据权限、敏感信息和审计约束",
                    true
            ));
        }
        if (containsAny(fullContext, "全量", "批量", "导出", "大数据", "性能", "异步")) {
            needs.add(new EvidenceNeed(
                    "PERFORMANCE",
                    base + " 性能 数据量 异步 限制",
                    "确认大数据量、并发、超时和异步处理要求",
                    false
            ));
        }
        return new EvidencePlan(planningMode, needs);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
