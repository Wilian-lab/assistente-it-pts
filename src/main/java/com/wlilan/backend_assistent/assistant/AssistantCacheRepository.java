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

  java.util.List<AssistantCacheEntry> findTop100ByItIdAndSetorAndIntentAndDocumentVersionAndModelOrderByLastAccessedAtDesc(
      UUID itId,
      String setor,
      String intent,
      String documentVersion,
      String model);

  Optional<AssistantCacheEntry> findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionOrderByLastAccessedAtDesc(
      UUID itId,
      String setor,
      String intent,
      String normalizedQuestion,
      String documentVersion);

  Optional<AssistantCacheEntry> findFirstByItIdAndSetorAndIntentAndNormalizedQuestionOrderByUpdatedAtDesc(
      UUID itId,
      String setor,
      String intent,
      String normalizedQuestion);

  java.util.List<AssistantCacheEntry> findTop100ByItIdAndSetorAndIntentAndDocumentVersionOrderByLastAccessedAtDesc(
      UUID itId,
      String setor,
      String intent,
      String documentVersion);

  java.util.List<AssistantCacheEntry> findTop100ByItIdAndSetorAndIntentOrderByUpdatedAtDesc(
      UUID itId,
      String setor,
      String intent);

  long deleteByItId(UUID itId);

  long deleteBySetor(String setor);
}
