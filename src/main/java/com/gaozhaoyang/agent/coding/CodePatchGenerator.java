package com.gaozhaoyang.agent.coding;

import com.gaozhaoyang.agent.workflow.WorkflowState;

public interface CodePatchGenerator {
    CodePatchPlan generate(WorkflowState workflow);
}
