package com.wlilan.backend_assistent.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public record AssistantOptionsRequest(
    @NotBlank(message = "IT selecionada e obrigatoria") String itId,
    String setorAtivo) {
}
