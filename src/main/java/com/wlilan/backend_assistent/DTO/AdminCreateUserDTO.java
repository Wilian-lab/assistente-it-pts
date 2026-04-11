package com.wlilan.backend_assistent.DTO;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.wlilan.backend_assistent.usuario.Cargo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminCreateUserDTO {

  @NotBlank(message = "Nome e obrigatorio")
  private String name;

  @NotBlank(message = "Email e obrigatorio")
  @Email(message = "Informe um email valido")
  private String email;

  @NotBlank(message = "Senha e obrigatoria")
  private String password;

  @NotBlank(message = "Role e obrigatoria")
  private String role;

  @NotNull(message = "Cargo e obrigatorio")
  private Cargo cargo;

  @NotBlank(message = "Setor e obrigatorio")
  @JsonAlias({ "setor", "setores" })
  private String setores;
}
