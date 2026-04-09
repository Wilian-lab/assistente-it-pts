package com.wlilan.backend_assistent.DTO;

import com.wlilan.backend_assistent.usuario.Cargo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminCreateUserDTO(
    @NotBlank(message = "Nome e obrigatorio") String name,
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Informe um email valido") String email,
    @NotBlank(message = "Senha e obrigatoria") String password,
    @NotNull(message = "Cargo e obrigatorio") Cargo cargo) {
}
