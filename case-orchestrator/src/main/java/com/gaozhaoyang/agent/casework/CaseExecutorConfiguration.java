package com.gaozhaoyang.agent.casework;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class CaseExecutorConfiguration {

    @Bean("caseTaskExecutor")
    TaskExecutor caseTaskExecutor(@Value("${app.cases.executor-threads:2}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(Math.max(threads, 4));
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("case-workflow-");
        executor.initialize();
        return executor;
    }
}
