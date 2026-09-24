package com.gaozhaoyang.agent.casework;

import jakarta.validation.constraints.NotBlank;

public record CaseReviewRequest(@NotBlank String comment) {
}
