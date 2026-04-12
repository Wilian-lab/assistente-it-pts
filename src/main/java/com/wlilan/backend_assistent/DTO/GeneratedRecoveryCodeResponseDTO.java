package com.wlilan.backend_assistent.DTO;

public record GeneratedRecoveryCodeResponseDTO(
    String message,
    String recoveryCode,
    boolean emailSent) {
}
