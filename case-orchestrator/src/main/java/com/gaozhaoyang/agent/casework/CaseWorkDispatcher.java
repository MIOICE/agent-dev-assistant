package com.gaozhaoyang.agent.casework;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CaseWorkDispatcher {

    private final TaskExecutor executor;
    private final CaseProcessor processor;
    private final CaseRepository repository;

    public CaseWorkDispatcher(@Qualifier("caseTaskExecutor") TaskExecutor executor,
                              CaseProcessor processor, CaseRepository repository) {
        this.executor = executor;
        this.processor = processor;
        this.repository = repository;
    }

    public void submit(String caseId) {
        executor.execute(() -> processor.process(caseId));
    }

    @Scheduled(fixedDelayString = "${app.cases.resume-delay-ms:5000}")
    public void resumeInterruptedWork() {
        repository.findRecoverable(20).forEach(item -> submit(item.caseId()));
    }
}
