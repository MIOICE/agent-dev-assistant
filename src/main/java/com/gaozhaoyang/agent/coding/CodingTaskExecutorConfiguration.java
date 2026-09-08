package com.gaozhaoyang.agent.coding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CodingTaskExecutorConfiguration {

    @Bean("codingTaskExecutor")
    public TaskExecutor codingTaskExecutor(
            @Value("${app.coding.executor.core-pool-size:2}") int corePoolSize,
            @Value("${app.coding.executor.queue-capacity:20}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("coding-agent-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
