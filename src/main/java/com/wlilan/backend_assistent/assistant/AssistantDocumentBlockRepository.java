package com.wlilan.backend_assistent.assistant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantDocumentBlockRepository extends JpaRepository<AssistantDocumentBlockEntity, UUID> {

  List<AssistantDocumentBlockEntity> findByItIdOrderByPageAscStepAsc(UUID itId);

  Optional<AssistantDocumentBlockEntity> findFirstByItIdOrderByUpdatedAtDesc(UUID itId);

  long deleteByItId(UUID itId);
}
