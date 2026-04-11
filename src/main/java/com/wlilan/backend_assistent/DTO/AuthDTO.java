package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthDTO(
    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Informe um email valido")
    String email,
    @NotBlank(message = "Setor e obrigatorio")
    String setor,
    @NotBlank(message = "Senha e obrigatoria")
    String password) {
}
