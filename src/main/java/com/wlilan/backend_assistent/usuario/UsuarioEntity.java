package com.wlilan.backend_assistent.usuario;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.validator.constraints.Length;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "Nome e obrigatorio")
  @Pattern(regexp = "^[\\p{L}]+\\s+[\\p{L}]+.*$", message = "Informe nome e sobrenome")
  private String name;

  @NotBlank(message = "Email e obrigatorio")
  @Email(message = "Informe um email valido")
  private String email;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @NotBlank(message = "Senha e obrigatoria")
  @Length(min = 8, message = "A senha deve conter no minimo 8 caracteres")
  private String password;

  @Enumerated(EnumType.STRING)
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Role role;

  @Enumerated(EnumType.STRING)
  private Cargo cargo;

  private String lastTrainedIt;

  private LocalDate lastTrainingDate;

  private Integer retrainingIntervalDays;

  private LocalDate nextTrainingDate;
}
