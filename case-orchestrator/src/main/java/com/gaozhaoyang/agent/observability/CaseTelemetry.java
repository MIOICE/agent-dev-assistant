package com.gaozhaoyang.agent.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Case 主流程的低敏感度观测入口。
 *
 * <p>Span 只记录流程阶段和内部标识，不记录需求正文、Prompt、证据内容或凭证。</p>
 */
@Component
public class CaseTelemetry {

    private final ObservationRegistry registry;

    public CaseTelemetry(ObservationRegistry registry) {
        this.registry = registry;
    }

    public <T> T observe(String name, String stage, String caseId, Supplier<T> action) {
        Observation observation = Observation.createNotStarted(name, registry)
                .lowCardinalityKeyValue("case.stage", stage)
                .highCardinalityKeyValue("case.id", caseId);
        return observation.observe(action);
    }
}
