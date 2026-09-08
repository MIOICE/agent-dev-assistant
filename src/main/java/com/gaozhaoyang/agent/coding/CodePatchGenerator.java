package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;
import com.gaozhaoyang.agent.skill.SkillActivation;

public interface CodePatchGenerator {
    CodePatchPlan generate(WorkflowState workflow, SkillActivation skills);
}
