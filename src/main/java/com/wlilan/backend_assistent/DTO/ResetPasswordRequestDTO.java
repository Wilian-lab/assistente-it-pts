package com.wlilan.backend_assistent.DTO;

import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequestDTO(
    @NotBlank(message = "Token e obrigatorio")
    String token,
    @NotBlank(message = "Nova senha e obrigatoria")
    @Length(min = 8, message = "A senha deve conter no minimo 8 caracteres")
    String newPassword) {
}
