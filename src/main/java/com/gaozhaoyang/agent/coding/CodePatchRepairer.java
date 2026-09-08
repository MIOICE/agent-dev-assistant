package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;

public interface CodePatchRepairer {

    CodePatchPlan repair(
            WorkflowState workflow,
            CodePatchPlan previousPlan,
            BuildVerification failedVerification,
            int repairAttempt
    );
}
