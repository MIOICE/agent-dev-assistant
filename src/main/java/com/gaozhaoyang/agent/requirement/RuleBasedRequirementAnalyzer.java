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
        List<String> missing = new ArrayList<>();
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


        if (modules.isEmpty()) {
            missing.add("请补充需求涉及的菜单、页面或业务模块");
        }
        if (!containsAny(normalized, "支持", "需要", "要求", "增加", "修改", "实现")) {
            missing.add("请补充期望发生的具体功能变化");
        }
if (normalized.contains("导出") && modules.isEmpty()) {
    missing.add("请补充需要导出的页面或业务模块");
}

if (normalized.contains("删除")) {
    missing.add("请确认删除范围以及是否允许恢复");
}

if (normalized.contains("批量修改")) {
    missing.add("请补充批量修改条件和目标字段");
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
        return new RequirementCard(
                title,
                normalized,
                modules,
                criteria,
                missing,
                priority,
                risks,
                List.of(),
                missing.isEmpty()
        );
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
