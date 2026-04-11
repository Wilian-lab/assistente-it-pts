package com.wlilan.backend_assistent.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantAskResponse;
import com.wlilan.backend_assistent.assistant.model.AssistantIntent;
import com.wlilan.backend_assistent.assistant.model.AssistantResponseMode;
import com.wlilan.backend_assistent.it.ItEntity;

@Service
public class AssistantCacheService {

  private static final String CACHE_NAMESPACE = "assistant-v22-setor-document-query-cache";

  private final AssistantCacheRepository assistantCacheRepository;
  private final AssistantIntentDetector intentDetector;

  public AssistantCacheService(
      AssistantCacheRepository assistantCacheRepository,
      AssistantIntentDetector intentDetector) {
    this.assistantCacheRepository = assistantCacheRepository;
    this.intentDetector = intentDetector;
  }

  public AssistantAskResponse findCachedResponse(
      ItEntity selectedIt,
      String setor,
      AssistantIntent intent,
      String normalizedQuestion,
      String documentVersion,
      String cacheModelKey) {
    var cachedEntry = this.assistantCacheRepository
        .findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionAndModel(
            selectedIt.getId(),
            normalizeSector(setor, selectedIt),
            intent.name(),
            normalizedQuestion,
            documentVersion,
            cacheModelKey)
        .orElse(null);

    if (cachedEntry == null) {
      return null;
    }

    cachedEntry.setHitCount((cachedEntry.getHitCount() == null ? 0L : cachedEntry.getHitCount()) + 1L);
    cachedEntry.setLastAccessedAt(LocalDateTime.now());
    cachedEntry.setUpdatedAt(LocalDateTime.now());
    var savedEntry = this.assistantCacheRepository.save(cachedEntry);

    return AssistantAskResponse.builder()
        .message(AssistantTextSanitizer.sanitize(savedEntry.getResponseMessage()))
        .sourceType("assistant_cache")
        .documento(AssistantTextSanitizer.sanitize(savedEntry.getDocumento()))
        .titulo(AssistantTextSanitizer.sanitize(savedEntry.getTitulo()))
        .revisao(AssistantTextSanitizer.sanitize(savedEntry.getRevisao()))
        .downloadUrl("/it/" + savedEntry.getItId() + "/file")
        .previewUrl("/it/" + savedEntry.getItId() + "/file")
        .warnings(List.of())
        .evidence(List.of())
        .metadata(Map.of(
            "mode", "database_cache",
            "model", savedEntry.getModel(),
            "intent", savedEntry.getIntent().toLowerCase(Locale.ROOT),
            "cacheHit", true,
            "createdAt", savedEntry.getCreatedAt().toString(),
            "hitCount", savedEntry.getHitCount()))
        .build();
  }

  public void saveResponse(
      ItEntity selectedIt,
      String setor,
      AssistantIntent intent,
      String normalizedQuestion,
      String documentVersion,
      String cacheModelKey,
      AssistantAskRequest request,
      String answer) {
    var now = LocalDateTime.now();
    var entry = this.assistantCacheRepository
        .findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionAndModel(
            selectedIt.getId(),
            normalizeSector(setor, selectedIt),
            intent.name(),
            normalizedQuestion,
            documentVersion,
            cacheModelKey)
        .orElseGet(AssistantCacheEntry::new);
    entry.setItId(selectedIt.getId());
    entry.setSetor(normalizeSector(setor, selectedIt));
    entry.setIntent(intent.name());
    entry.setNormalizedQuestion(normalizedQuestion);
    entry.setDocumentVersion(documentVersion);
    entry.setModel(cacheModelKey);
    entry.setDocumento(firstNonBlank(selectedIt.getDocumento(), request.documentCode()));
    entry.setTitulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()));
    entry.setRevisao(AssistantTextSanitizer.sanitize(selectedIt.getRevisao()));
    entry.setResponseMessage(AssistantTextSanitizer.sanitize(answer));
    if (entry.getCreatedAt() == null) {
      entry.setCreatedAt(now);
    }
    entry.setUpdatedAt(now);
    entry.setLastAccessedAt(now);
    entry.setHitCount(entry.getHitCount() == null ? 1L : entry.getHitCount() + 1L);
    this.assistantCacheRepository.save(entry);
  }

  public String resolveDocumentVersion(ItEntity selectedIt) {
    if (hasText(selectedIt.getFileUrl())) {
      var path = Path.of(selectedIt.getFileUrl());
      if (Files.exists(path)) {
        try {
          var size = Files.size(path);
          var lastModified = Files.getLastModifiedTime(path).toMillis();
          return this.intentDetector.normalize(CACHE_NAMESPACE + "|" + path.toAbsolutePath() + "|" + size + "|" + lastModified);
        } catch (IOException ignored) {
          // fall through
        }
      }
    }

    return this.intentDetector.normalize(String.join("|",
        CACHE_NAMESPACE,
        firstNonBlank(selectedIt.getDocumento(), "-"),
        firstNonBlank(selectedIt.getRevisao(), "-"),
        selectedIt.getDataPublicacao() == null ? "-" : selectedIt.getDataPublicacao().toString()));
  }

  public String buildCacheModelKey(AssistantResponseMode responseMode, AssistantIntent intent, String openRouterModel) {
    var modelKey = firstNonBlank(openRouterModel, "default");
    return CACHE_NAMESPACE
        + "|" + responseMode.name().toLowerCase(Locale.ROOT)
        + "|" + intent.name().toLowerCase(Locale.ROOT)
        + "|" + modelKey;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String normalizeSector(String setor, ItEntity selectedIt) {
    return firstNonBlank(setor, selectedIt.getSetor(), "GLOBAL").toUpperCase(Locale.ROOT);
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return AssistantTextSanitizer.sanitize(value).trim();
      }
    }
    return "";
  }
}
