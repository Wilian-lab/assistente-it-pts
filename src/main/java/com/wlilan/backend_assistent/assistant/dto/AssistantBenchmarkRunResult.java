package com.wlilan.backend_assistent.assistant.dto;

import java.util.Map;

import lombok.Builder;

@Builder
public record AssistantBenchmarkRunResult(
    String model,
    long elapsedMs,
    String sourceType,
    String answer,
    Map<String, Object> metadata,
    String error) {
}
