package com.wlilan.backend_assistent.DTO;

import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecoveryCodeResetRequestDTO(
    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Informe um email valido")
    String email,
    @NotBlank(message = "Codigo de recuperacao e obrigatorio")
    String recoveryCode,
    @NotBlank(message = "Nova senha e obrigatoria")
    @Length(min = 8, message = "A senha deve conter no minimo 8 caracteres")
    String newPassword) {
}
