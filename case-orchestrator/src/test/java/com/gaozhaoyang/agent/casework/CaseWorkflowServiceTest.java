package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.casework.security.CaseActor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseWorkflowServiceTest {

    @Test
    void shouldKeepLocalCaseCanceledWhenBestEffortRemoteCancelFails() {
        CaseRepository repository = mock(CaseRepository.class);
        CaseWorkDispatcher dispatcher = mock(CaseWorkDispatcher.class);
        EvidenceResearchGateway gateway = mock(EvidenceResearchGateway.class);
        CaseWorkflowService service = new CaseWorkflowService(repository, dispatcher, gateway);
        CaseActor actor = new CaseActor("impl-1", "tenant-a", Set.of("IMPLEMENTER"));
        RequirementCase item = RequirementCase.create("case-1", "tenant-a", "impl-1",
                        "订单导出", new CustomerSystemSnapshot("mes", "v13.1", "订单"), Instant.now())
                .progress(CaseStage.RESEARCHING_EVIDENCE, null, "remote-task-1", null,
                        null, null, null, null, null, Instant.now());
        when(repository.findByTenantAndId("tenant-a", "case-1")).thenReturn(Optional.of(item));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("remote unavailable"))
                .when(gateway).cancel("tenant-a", "remote-task-1");

        RequirementCase canceled = service.cancel(actor, "case-1", new CaseReviewRequest("需求撤销"));

        assertThat(canceled.stage()).isEqualTo(CaseStage.CANCELED);
        assertThat(canceled.failureCode()).isEqualTo("CANCELED_BY_IMPLEMENTER");
        verify(gateway).cancel("tenant-a", "remote-task-1");
        verify(repository).audit(eq("case-1"), eq("tenant-a"), eq("impl-1"),
                eq("IMPLEMENTER"), eq("CASE_CANCELED"), any());
    }
}
