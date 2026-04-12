package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(
    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Informe um email valido")
    String email) {
}
