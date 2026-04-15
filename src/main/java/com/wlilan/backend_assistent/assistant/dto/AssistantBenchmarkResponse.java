package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record AssistantBenchmarkResponse(
    String itId,
    String documento,
    String titulo,
    List<String> models,
    List<AssistantBenchmarkQuestionResult> questions) {
}
