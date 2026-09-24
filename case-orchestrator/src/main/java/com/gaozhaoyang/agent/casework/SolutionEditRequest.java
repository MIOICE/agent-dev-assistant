package com.gaozhaoyang.agent.casework;

import com.gaozhaoyang.agent.solution.TechnicalSolution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SolutionEditRequest(
        @Valid @NotNull TechnicalSolution solution,
        @NotBlank String changeReason
) {
}
