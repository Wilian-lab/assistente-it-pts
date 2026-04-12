package com.wlilan.backend_assistent.DTO;

import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordDTO {

  @NotBlank(message = "A senha atual e obrigatoria")
  private String currentPassword;

  @NotBlank(message = "A nova senha e obrigatoria")
  @Length(min = 8, message = "A nova senha deve conter no minimo 8 caracteres")
  private String newPassword;
}
