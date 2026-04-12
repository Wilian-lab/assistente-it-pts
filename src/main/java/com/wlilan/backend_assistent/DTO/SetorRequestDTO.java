package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.NotBlank;

public record SetorRequestDTO(
    @NotBlank(message = "Codigo do setor e obrigatorio")
    String codigo) {
}
