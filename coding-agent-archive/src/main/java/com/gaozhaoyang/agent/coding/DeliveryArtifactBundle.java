package com.gaozhaoyang.agent.coding;

public record DeliveryArtifactBundle(
        String fileName,
        byte[] content,
        String sha256,
        DeliveryArtifactManifest manifest
) {
    public DeliveryArtifactBundle {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
