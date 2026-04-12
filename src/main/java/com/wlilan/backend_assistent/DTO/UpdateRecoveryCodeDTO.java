package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.NotBlank;

public record UpdateRecoveryCodeDTO(
    @NotBlank(message = "Codigo de recuperacao e obrigatorio")
    String recoveryCode) {
}
