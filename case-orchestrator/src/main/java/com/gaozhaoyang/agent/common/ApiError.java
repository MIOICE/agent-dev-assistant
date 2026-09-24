package com.gaozhaoyang.agent.common;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp) {
}
