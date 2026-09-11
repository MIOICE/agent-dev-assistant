package com.gaozhaoyang.agent.observability;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 在同步 Agent 调用链中传播最小 Trace 上下文。
 *
 * <p>这里只保存关联 ID，不保存 Prompt、业务正文或密钥。异步任务使用自身持久化的
 * workflowId/taskId 关联，因此不会依赖 ThreadLocal 跨线程传播。</p>
 */
@Component
public class AgentTraceContext {

    private final ThreadLocal<String> currentTraceId = new ThreadLocal<>();

    public <T> T withinTrace(String traceId, Supplier<T> action) {
        if (traceId == null || traceId.isBlank()) {
            return action.get();
        }
        String previous = currentTraceId.get();
        currentTraceId.set(traceId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                currentTraceId.remove();
            } else {
                currentTraceId.set(previous);
            }
        }
    }

    public Optional<String> currentTraceId() {
        return Optional.ofNullable(currentTraceId.get());
    }
}
