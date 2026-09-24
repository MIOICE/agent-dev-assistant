package com.gaozhaoyang.agent.coding;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class DeliveryArtifactVerificationController {

    private final DeliveryArtifactVerificationService verificationService;

    public DeliveryArtifactVerificationController(
            DeliveryArtifactVerificationService verificationService
    ) {
        this.verificationService = verificationService;
    }

    @PostMapping(
            path = "/api/delivery-artifacts/verify",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public DeliveryArtifactVerificationResult verify(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String expectedSha256
    ) throws IOException {
        return verificationService.verify(file.getBytes(), expectedSha256);
    }
}
