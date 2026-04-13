package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserSetoresDTO(
    @NotBlank(message = "Informe ao menos um setor")
    String setores) {
}
