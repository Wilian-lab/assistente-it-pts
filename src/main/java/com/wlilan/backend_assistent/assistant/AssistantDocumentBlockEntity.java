package com.wlilan.backend_assistent.assistant;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(
    name = "assistant_document_block",
    indexes = {
        @Index(name = "idx_assistant_document_block_it", columnList = "itId"),
        @Index(name = "idx_assistant_document_block_lookup", columnList = "itId,page,step,entryType"),
        @Index(name = "idx_assistant_document_block_setor", columnList = "setor"),
        @Index(name = "idx_assistant_document_block_setor_documento", columnList = "setor,documento")
    })
public class AssistantDocumentBlockEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID itId;

  @Column(nullable = false, length = 255)
  private String documento;

  @Column(nullable = false, length = 120)
  private String setor;

  @Column(length = 255)
  private String titulo;

  @Column(length = 80)
  private String revisao;

  @Column(length = 80)
  private String status;

  @Column(length = 255)
  private String author;

  @Column(length = 255)
  private String authorizer;

  @Column(length = 80)
  private String printDate;

  @Column(length = 80)
  private String createDate;

  private Integer page;

  private Integer step;

  private Integer sectionNumber;

  @Column(length = 255)
  private String sectionTitle;

  @Column(nullable = false, length = 40)
  private String entryType;

  @Column(columnDefinition = "TEXT")
  private String what;

  @Column(columnDefinition = "TEXT")
  private String how;

  @Column(columnDefinition = "TEXT")
  private String care;

  @Column(columnDefinition = "TEXT")
  private String possibleCauses;

  @Column(columnDefinition = "TEXT")
  private String actionText;

  @Column(columnDefinition = "TEXT")
  private String normalized;

  @Column(columnDefinition = "TEXT")
  private String normalizedWhat;

  @Column(columnDefinition = "TEXT")
  private String normalizedHow;

  @Column(columnDefinition = "TEXT")
  private String normalizedCare;

  @Column(nullable = false, length = 255)
  private String sourceHash;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
