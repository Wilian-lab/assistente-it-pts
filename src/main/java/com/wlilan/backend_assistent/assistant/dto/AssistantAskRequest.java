package com.wlilan.backend_assistent.assistant.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record AssistantAskRequest(
    @NotBlank(message = "IT selecionada e obrigatoria") String itId,
    @NotBlank(message = "Mensagem e obrigatoria") String message,
    String documentCode,
    String documentTitle,
    String fileUrl,
    String setorAtivo,
    Integer selectedStep,
    Integer selectedPage,
    String selectedOptionTitle,
    List<AssistantChatTurn> history) {
}
