package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "mock", matchIfMissing = true)
public class RuleBasedCodePatchRepairer implements CodePatchRepairer {

    @Override
    public CodePatchPlan repair(
            WorkflowState workflow,
            CodePatchPlan previousPlan,
            BuildVerification failedVerification,
            int repairAttempt
    ) {
        // mock模式的初始代码本来就可以通过。这里保持确定性，便于演示有界循环。
        return previousPlan;
    }
}
