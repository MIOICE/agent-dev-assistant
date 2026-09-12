package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingDeliveryArtifactControllerTest {

    @Test
    void shouldReturnAttachmentWithArtifactDigest() {
        CodingDeliveryArtifactService service = mock(CodingDeliveryArtifactService.class);
        DeliveryArtifactManifest manifest = new DeliveryArtifactManifest(
                "agent-delivery-v1", "task-1", "workflow-1", 1,
                "2026-09-12T02:00:00Z", "summary",
                new DeliveryArtifactManifest.Verification(
                        true, "mvn test", 0, 20, "build-hash"),
                AutonomyBudget.safeDefault(), 3, 1, 0,
                List.of(), List.of()
        );
        when(service.build("task-1")).thenReturn(new DeliveryArtifactBundle(
                "coding-delivery-task-1.zip",
                new byte[]{1, 2, 3},
                "bundle-hash",
                manifest
        ));
        CodingDeliveryArtifactController controller =
                new CodingDeliveryArtifactController(service);

        ResponseEntity<byte[]> response = controller.download("task-1");

        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/zip");
        assertThat(response.getHeaders().getFirst("X-Artifact-SHA256"))
                .isEqualTo("bundle-hash");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("coding-delivery-task-1.zip");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }
}
