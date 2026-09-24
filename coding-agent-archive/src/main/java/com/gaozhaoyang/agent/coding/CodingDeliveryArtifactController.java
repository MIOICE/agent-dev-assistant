package com.gaozhaoyang.agent.coding;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coding-tasks/{taskId}/delivery")
public class CodingDeliveryArtifactController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final CodingDeliveryArtifactService artifactService;

    public CodingDeliveryArtifactController(CodingDeliveryArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping("/metadata")
    public DeliveryArtifactMetadata metadata(@PathVariable String taskId) {
        return DeliveryArtifactMetadata.from(artifactService.build(taskId));
    }

    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        DeliveryArtifactBundle bundle = artifactService.build(taskId);
        byte[] content = bundle.content();
        return ResponseEntity.ok()
                .contentType(ZIP)
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(bundle.fileName())
                                .build().toString())
                .header("X-Artifact-SHA256", bundle.sha256())
                .body(content);
    }
}
