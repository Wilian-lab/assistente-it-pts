package com.wlilan.backend_assistent.pts;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity(name = "pts_file")
@Data
public class PtsFileEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String setor;

  private String fileName;

  private String path;

  private Long size;

  private LocalDateTime lastModified;
}
