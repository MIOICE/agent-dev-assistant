package com.gaozhaoyang.agent.casework;

import jakarta.validation.constraints.NotBlank;

public record CustomerSystemSnapshot(
        @NotBlank String systemId,
        @NotBlank String systemVersion,
        @NotBlank String module
) {
}
