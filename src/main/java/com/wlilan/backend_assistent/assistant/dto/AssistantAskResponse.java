package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder
public record AssistantAskResponse(
    String message,
    String sourceType,
    String documento,
    String titulo,
    String revisao,
    String downloadUrl,
    String previewUrl,
    List<String> warnings,
    java.util.List<AssistantEvidenceItem> evidence,
    Map<String, Object> metadata) {
}
