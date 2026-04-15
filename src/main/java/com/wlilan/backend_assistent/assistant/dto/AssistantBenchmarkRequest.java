package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record AssistantBenchmarkRequest(
    @NotBlank(message = "IT selecionada e obrigatoria") String itId,
    String setorAtivo,
    @NotEmpty(message = "Informe pelo menos uma pergunta") List<String> questions,
    List<String> models) {
}
