package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.NotBlank;

public record SwitchSetorDTO(
    @NotBlank(message = "Setor e obrigatorio")
    String setor) {
}
