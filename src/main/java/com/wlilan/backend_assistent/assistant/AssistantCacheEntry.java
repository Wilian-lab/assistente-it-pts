package com.wlilan.backend_assistent.assistant;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(
    name = "assistant_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_assistant_cache_lookup",
        columnNames = {"itId", "setor", "intent", "normalizedQuestion", "documentVersion", "model"}))
public class AssistantCacheEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID itId;

  @Column(length = 120)
  private String setor;

  @Column(nullable = false, length = 120)
  private String intent;

  @Column(nullable = false, length = 512)
  private String normalizedQuestion;

  @Column(nullable = false, length = 512)
  private String documentVersion;

  @Column(nullable = false, length = 160)
  private String model;

  @Column(nullable = false, length = 255)
  private String documento;

  @Column(length = 255)
  private String titulo;

  @Column(length = 80)
  private String revisao;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String responseMessage;

  @Column(length = 120)
  private String originalSourceType;

  @Column(length = 80)
  private String originalProvider;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(nullable = false)
  private LocalDateTime lastAccessedAt;

  @Column(nullable = false)
  private Long hitCount;
}
