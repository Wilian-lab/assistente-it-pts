package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record AssistantBenchmarkQuestionResult(
    String question,
    List<AssistantBenchmarkRunResult> runs) {
}
