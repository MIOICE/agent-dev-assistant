package com.gaozhaoyang.agent.requirement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.ai.mode",
        havingValue = "fixed"
)
public class FixedRequirementAnalyzer implements RequirementAnalyzer {

    @Override
    public RequirementCard analyze(String content) {
        return new RequirementCard(
                "固定测试需求",
                content,
                List.of("测试模块"),
                List.of("接口能够正常返回固定结果"),
                List.of(),
                "P2",
                List.of(),
                List.of(),
                true
        );
    }
}
