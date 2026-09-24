package com.gaozhaoyang.agent.solution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static com.gaozhaoyang.agent.solution.EvaluationGatePolicyTest.report;
import static com.gaozhaoyang.agent.solution.EvaluationGatePolicyTest.run;
import static org.assertj.core.api.Assertions.assertThat;

class FileEvaluationRunRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldPersistAndReloadEvaluationRun() {
        FileEvaluationRunRepository repository = new FileEvaluationRunRepository(
                new ObjectMapper(), temporaryDirectory.resolve("runs").toString());
        ClaimEvidenceEvaluationRun expected = run(
                "run-1",
                report(1, 0.92, 1, 0.75, 1, 0.9167, 12),
                EvaluationGateStatus.PASSED
        );

        repository.save(expected);

        assertThat(repository.findAll(20))
                .singleElement()
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldUseLatestPassingRunAsBaselineInsteadOfFailedRun() {
        FileEvaluationRunRepository repository = new FileEvaluationRunRepository(
                new ObjectMapper(), temporaryDirectory.resolve("runs").toString());
        repository.save(run(
                "passed",
                report(1, 0.92, 1, 0.75, 1, 0.9167, 12),
                EvaluationGateStatus.PASSED
        ));
        repository.save(run(
                "failed",
                report(1, 0.80, 1, 0.50, 1, 0.8333, 12),
                EvaluationGateStatus.FAILED
        ));

        assertThat(repository.findLatestPassing())
                .get()
                .extracting(ClaimEvidenceEvaluationRun::runId)
                .isEqualTo("passed");
    }
}
