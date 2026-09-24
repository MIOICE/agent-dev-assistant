package com.gaozhaoyang.agent.casework;

import java.time.Instant;

public record CaseApproval(String approvedBy, String comment, Instant approvedAt) {
}
