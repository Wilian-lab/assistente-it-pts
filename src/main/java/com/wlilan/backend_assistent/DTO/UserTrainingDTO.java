package com.wlilan.backend_assistent.DTO;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserTrainingDTO {

  @NotBlank(message = "A ultima IT treinada e obrigatoria")
  private String lastTrainedIt;

  @NotBlank(message = "O status do treinamento e obrigatorio")
  private String trainingStatus;

  @NotNull(message = "A data do ultimo treinamento e obrigatoria")
  private LocalDate lastTrainingDate;

  @NotNull(message = "O prazo para o proximo treinamento e obrigatorio")
  @Min(value = 1, message = "O prazo para o proximo treinamento deve ser maior que zero")
  private Integer retrainingIntervalDays;
}
