package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryArtifactVerificationControllerTest {

    @Test
    void shouldForwardUploadedArtifactAndTrustedDigest() throws Exception {
        DeliveryArtifactVerificationService service =
                mock(DeliveryArtifactVerificationService.class);
        byte[] content = {1, 2, 3};
        DeliveryArtifactVerificationResult expected =
                new DeliveryArtifactVerificationResult(
                        true, "digest", "MATCH", "agent-delivery-v2",
                        "task-1", "workflow-1", 6, 128,
                        List.of("verified"), List.of(), List.of());
        when(service.verify(content, "digest")).thenReturn(expected);
        DeliveryArtifactVerificationController controller =
                new DeliveryArtifactVerificationController(service);
        MockMultipartFile file = new MockMultipartFile(
                "file", "delivery.zip", "application/zip", content);

        DeliveryArtifactVerificationResult actual =
                controller.verify(file, "digest");

        assertThat(actual).isSameAs(expected);
        verify(service).verify(content, "digest");
    }
}
