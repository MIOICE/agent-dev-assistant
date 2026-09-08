package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;
import com.gaozhaoyang.agent.skill.SkillActivation;

public interface CodePatchRepairer {

    CodePatchPlan repair(
            WorkflowState workflow,
            CodePatchPlan previousPlan,
            BuildVerification failedVerification,
            int repairAttempt,
            SkillActivation skills
    );
}
