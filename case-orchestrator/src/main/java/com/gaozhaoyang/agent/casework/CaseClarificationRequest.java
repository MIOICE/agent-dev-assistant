package com.gaozhaoyang.agent.casework;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CaseClarificationRequest(
        @NotEmpty @Size(max = 20) Map<String, @Size(max = 1000) String> answers
) {
}
