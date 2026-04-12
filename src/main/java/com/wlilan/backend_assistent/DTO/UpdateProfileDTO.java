package com.wlilan.backend_assistent.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileDTO {

  @NotBlank(message = "Nome e obrigatorio")
  @Pattern(regexp = "^[\\p{L}]+\\s+[\\p{L}]+.*$", message = "Informe nome e sobrenome")
  private String name;
}
