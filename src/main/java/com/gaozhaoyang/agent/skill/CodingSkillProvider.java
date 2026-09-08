package com.gaozhaoyang.agent.skill;

public interface CodingSkillProvider {
    SkillActivation activate(CodingSkillPhase phase, String taskContext);
}
