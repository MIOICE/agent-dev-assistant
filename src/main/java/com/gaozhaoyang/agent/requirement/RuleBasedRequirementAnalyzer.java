package com.gaozhaoyang.agent.requirement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class RuleBasedRequirementAnalyzer implements RequirementAnalyzer {

    @Override
    public RequirementCard analyze(String content) {
        String normalized = content.trim().replaceAll("\\s+", " ");
        List<String> modules = detectModules(normalized);
        boolean hasKnownModule = !modules.isEmpty();
        List<ClarificationQuestion> questions = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        String priority = "P2";
        List<String> risks = new ArrayList<>();
        if(containsAny(normalized,"紧急","今天","立即")){
            priority = "P0";
        }else if(containsAny(normalized, "上线前","本周"))
            {
                priority ="P1";
            }

            if (normalized.contains("全量导出")) {
    risks.add("全量导出可能造成数据库压力，需要确认数据范围");
}

if (normalized.contains("删除")) {
    risks.add("删除操作可能导致数据丢失，需要确认恢复方案");
}

if (normalized.contains("批量修改")) {
    risks.add("批量修改影响范围较大，需要确认筛选条件和目标字段");
}


        if (modules.isEmpty() && normalized.contains("导出")) {
            modules.add("通用导出");
            questions.add(blockingQuestion(
                    "范围",
                    "需要导出哪个业务对象？",
                    List.of("当前所在页面的数据", "订单数据", "报表数据", "其他（手动填写）"),
                    "当前所在页面的数据"
            ));
        } else if (modules.isEmpty()) {
            questions.add(blockingQuestion(
                    "范围",
                    "这个需求发生在哪个菜单或业务模块？",
                    List.of("当前所在页面", "订单管理", "报表管理", "其他（手动填写）"),
                    "当前所在页面"
            ));
        }
        if (!containsAny(normalized, "支持", "需要", "要求", "增加", "修改", "实现")) {
            questions.add(blockingQuestion(
                    "业务规则",
                    "你希望系统完成什么操作？",
                    List.of("新增功能", "修改现有功能", "修复异常", "其他（手动填写）"),
                    "新增功能"
            ));
        }
        boolean acceptedRecommendations = normalized.contains("用户已明确接受Agent推荐值");
        if (normalized.contains("导出") && !acceptedRecommendations) {
            if (!containsAny(normalized, "筛选", "当前页", "全部", "全量")) {
                questions.add(new ClarificationQuestion(
                        "范围",
                        "导出范围按什么确定？",
                        List.of("当前筛选结果", "当前页数据", "全部有权限的数据"),
                        "当前筛选结果",
                        false
                ));
            }
            if (!containsAny(normalized, "管理员", "权限", "角色")) {
                questions.add(new ClarificationQuestion(
                        "权限",
                        "哪些用户可以执行导出？",
                        List.of("沿用页面现有权限", "仅管理员", "指定业务角色"),
                        "沿用页面现有权限",
                        false
                ));
            }
            questions.add(new ClarificationQuestion(
                    "其他",
                    "默认使用什么文件格式？",
                    List.of("Excel", "CSV"),
                    "Excel",
                    false
            ));
            assumptions.add("超过一万条数据时默认转为异步导出，并记录审计日志");
            if (hasKnownModule) {
                assumptions.add("默认沿用目标页面现有的数据权限边界");
            }
        }

        if (normalized.contains("删除") && !acceptedRecommendations) {
            questions.add(blockingQuestion(
                    "业务规则",
                    "删除后是否需要支持恢复？",
                    List.of("逻辑删除，可恢复", "物理删除，不可恢复"),
                    "逻辑删除，可恢复"
            ));
        }

        if (normalized.contains("批量修改") && !acceptedRecommendations
                && !containsAny(normalized, "选中", "筛选", "状态", "字段")) {
            questions.add(blockingQuestion(
                    "范围",
                    "批量修改哪些数据？",
                    List.of("用户勾选的数据", "当前筛选结果", "全部有权限的数据"),
                    "用户勾选的数据"
            ));
        }
        List<String> criteria = new ArrayList<>();
        criteria.add("在目标页面能够完成需求描述中的核心操作");
        criteria.add("原有业务流程和历史数据不受影响");
        if (containsAny(normalized, "导出", "报表")) {
            criteria.add("导出数据与页面筛选条件及展示结果保持一致");
        }

        String titleSource = normalized.split("用户补充信息：", 2)[0].trim();
        String title = titleSource.length() <= 28
                ? titleSource
                : titleSource.substring(0, 28) + "…";
        List<String> missing = questions.stream()
                .filter(ClarificationQuestion::blocking)
                .map(ClarificationQuestion::question)
                .toList();
        return new RequirementCard(
                title,
                normalized,
                modules,
                criteria,
                missing,
                priority,
                risks,
                List.of(),
                questions,
                assumptions,
                missing.isEmpty()
        );
    }

    private ClarificationQuestion blockingQuestion(
            String category,
            String question,
            List<String> options,
            String recommendedAnswer
    ) {
        return new ClarificationQuestion(category, question, options, recommendedAnswer, true);
    }

    private List<String> detectModules(String content) {
        List<String> modules = new ArrayList<>();
        addIfContains(modules, content, "订单", "订单管理");
        addIfContains(modules, content, "报表", "报表管理");
        addIfContains(modules, content, "批次", "车间作业");
        addIfContains(modules, content, "设备", "设备管理");
        addIfContains(modules, content, "用户", "用户与权限");
        return modules;
    }

    private void addIfContains(List<String> modules, String content, String keyword, String module) {
        if (content.contains(keyword) && !modules.contains(module)) {
            modules.add(module);
        }
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
