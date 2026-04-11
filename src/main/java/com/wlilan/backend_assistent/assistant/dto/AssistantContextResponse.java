package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder
public record AssistantContextResponse(
    String itId,
    String documento,
    String titulo,
    String revisao,
    String status,
    String setor,
    String downloadUrl,
    String previewUrl,
    String conversationId,
    String documentVersion,
    Integer stepCount,
    Integer anomalyCount,
    List<AssistantOptionItem> opcoes,
    List<String> sampleQuestions,
    Map<String, Object> metadata) {
}
