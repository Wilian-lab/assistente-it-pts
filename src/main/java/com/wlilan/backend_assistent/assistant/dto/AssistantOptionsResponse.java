package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record AssistantOptionsResponse(
    String documento,
    String titulo,
    List<AssistantOptionItem> opcoes) {
}
