package com.wlilan.backend_assistent.pts;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity(name = "pts_record")
@Data
public class PtsRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(columnDefinition = "TEXT")
  private String setor;
  @Column(columnDefinition = "TEXT")
  private String produto;
  @Column(columnDefinition = "TEXT")
  private String etapa;
  @Column(columnDefinition = "TEXT")
  private String item;
  @Column(columnDefinition = "TEXT")
  private String variavel;
  @Column(columnDefinition = "TEXT")
  private String classificacao;
  @Column(columnDefinition = "TEXT")
  private String unidade;
  @Column(columnDefinition = "TEXT")
  private String limiteInf;
  @Column(columnDefinition = "TEXT")
  private String limiteSup;
  @Column(columnDefinition = "TEXT")
  private String respColeta;
  @Column(columnDefinition = "TEXT")
  private String respAnalise;
  @Column(columnDefinition = "TEXT")
  private String frequencia;
  @Column(columnDefinition = "TEXT")
  private String pontoColeta;
  @Column(columnDefinition = "TEXT")
  private String amostra;
  @Column(columnDefinition = "TEXT")
  private String metodoAnalise;
  @Column(columnDefinition = "TEXT")
  private String tag;
  @Column(columnDefinition = "TEXT")
  private String tagAspen;
  @Column(columnDefinition = "TEXT")
  private String acaoAbaixo;
  @Column(columnDefinition = "TEXT")
  private String acaoAcima;
  @Column(columnDefinition = "TEXT")
  private String fca;
  @Column(columnDefinition = "TEXT")
  private String vaiNoApp;
  @Column(columnDefinition = "TEXT")
  private String documentoReferencia;
}
