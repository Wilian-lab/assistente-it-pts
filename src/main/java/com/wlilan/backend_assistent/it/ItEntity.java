package com.wlilan.backend_assistent.it;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity(name = "it")
@Data
public class ItEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotBlank(message = "Documento e obrigatorio")
  private String documento;

  @NotBlank(message = "Revisao e obrigatoria")
  private String revisao;

  @NotBlank(message = "Status e obrigatorio")
  private String status;

  @NotNull(message = "Data de publicacao e obrigatoria")
  private LocalDateTime dataPublicacao;

  @NotNull(message = "Pagina atual e obrigatoria")
  @Min(value = 1, message = "Pagina atual deve ser maior que zero")
  private Integer paginaAtual;

  @NotNull(message = "Total de paginas e obrigatorio")
  @Min(value = 1, message = "Total de paginas deve ser maior que zero")
  private Integer totalPaginas;

  @NotNull(message = "Prazo de treinamento e obrigatorio")
  @Min(value = 0, message = "Prazo de treinamento nao pode ser negativo")
  private Integer prazoTreinamentoDias;
}
