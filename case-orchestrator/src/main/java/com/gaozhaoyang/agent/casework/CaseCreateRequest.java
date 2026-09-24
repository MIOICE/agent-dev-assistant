package com.gaozhaoyang.agent.casework;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CaseCreateRequest(
        @NotBlank @Size(max = 4000) String requirement,
        @Valid @NotNull CustomerSystemSnapshot system
) {
}
