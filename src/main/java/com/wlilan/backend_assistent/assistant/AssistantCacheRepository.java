package com.wlilan.backend_assistent.assistant;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantCacheRepository extends JpaRepository<AssistantCacheEntry, UUID> {

  Optional<AssistantCacheEntry> findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionAndModel(
      UUID itId,
      String setor,
      String intent,
      String normalizedQuestion,
      String documentVersion,
      String model);

  long deleteByItId(UUID itId);

  long deleteBySetor(String setor);
}
