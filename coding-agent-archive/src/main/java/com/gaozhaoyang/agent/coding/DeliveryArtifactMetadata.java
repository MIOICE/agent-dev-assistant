package com.gaozhaoyang.agent.coding;

public record DeliveryArtifactMetadata(
        String fileName,
        long bytes,
        String sha256,
        DeliveryArtifactManifest manifest
) {
    public static DeliveryArtifactMetadata from(DeliveryArtifactBundle bundle) {
        return new DeliveryArtifactMetadata(
                bundle.fileName(), bundle.content().length,
                bundle.sha256(), bundle.manifest());
    }
}
