package com.wlilan.backend_assistent.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantAskResponse;
import com.wlilan.backend_assistent.assistant.model.AssistantIntent;
import com.wlilan.backend_assistent.assistant.model.AssistantResponseMode;
import com.wlilan.backend_assistent.it.ItEntity;

@Service
public class AssistantCacheService {

  private static final String CACHE_NAMESPACE = "assistant-v24-setor-document-query-structured-rich-cache";

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
    var normalizedSector = normalizeSector(setor, selectedIt);
    var cachedEntry = this.assistantCacheRepository
        .findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionAndModel(
            selectedIt.getId(),
            normalizedSector,
            intent.name(),
            normalizedQuestion,
            documentVersion,
            cacheModelKey)
        .orElse(null);

    if (cachedEntry == null) {
      cachedEntry = findSimilarCachedEntry(
          selectedIt,
          normalizedSector,
          intent,
          normalizedQuestion,
          documentVersion,
          cacheModelKey);
    }

    if (cachedEntry == null) {
      cachedEntry = this.assistantCacheRepository
          .findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionOrderByLastAccessedAtDesc(
              selectedIt.getId(),
              normalizedSector,
              intent.name(),
              normalizedQuestion,
              documentVersion)
          .orElse(null);
    }

    if (cachedEntry == null) {
      cachedEntry = findSimilarCachedEntryIgnoringModel(
          selectedIt,
          normalizedSector,
          intent,
          normalizedQuestion,
          documentVersion);
    }

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
    var normalizedSector = normalizeSector(setor, selectedIt);
    var entry = this.assistantCacheRepository
        .findFirstByItIdAndSetorAndIntentAndNormalizedQuestionAndDocumentVersionAndModel(
            selectedIt.getId(),
            normalizedSector,
            intent.name(),
            normalizedQuestion,
            documentVersion,
            cacheModelKey)
        .orElseGet(() -> {
          var reusableExisting = findReusableCachedEntry(
              selectedIt,
              normalizedSector,
              intent,
              normalizedQuestion,
              documentVersion);
          return reusableExisting != null ? reusableExisting : new AssistantCacheEntry();
        });
    entry.setItId(selectedIt.getId());
    entry.setSetor(normalizedSector);
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

  public String buildCacheModelKey(AssistantResponseMode responseMode, AssistantIntent intent) {
    var modelKey = "gemini-primary";
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

  private AssistantCacheEntry findSimilarCachedEntry(
      ItEntity selectedIt,
      String normalizedSector,
      AssistantIntent intent,
      String normalizedQuestion,
      String documentVersion,
      String cacheModelKey) {
    var candidates = this.assistantCacheRepository
        .findTop100ByItIdAndSetorAndIntentAndDocumentVersionAndModelOrderByLastAccessedAtDesc(
            selectedIt.getId(),
            normalizedSector,
            intent.name(),
            documentVersion,
            cacheModelKey);

    AssistantCacheEntry bestCandidate = null;
    double bestScore = 0d;
    for (var candidate : candidates) {
      var score = similarityScore(normalizedQuestion, candidate.getNormalizedQuestion());
      if (score > bestScore) {
        bestScore = score;
        bestCandidate = candidate;
      }
    }

    return bestScore >= 0.97d ? bestCandidate : null;
  }

  private AssistantCacheEntry findSimilarCachedEntryIgnoringModel(
      ItEntity selectedIt,
      String normalizedSector,
      AssistantIntent intent,
      String normalizedQuestion,
      String documentVersion) {
    var candidates = this.assistantCacheRepository
        .findTop100ByItIdAndSetorAndIntentAndDocumentVersionOrderByLastAccessedAtDesc(
            selectedIt.getId(),
            normalizedSector,
            intent.name(),
            documentVersion);

    AssistantCacheEntry bestCandidate = null;
    double bestScore = 0d;
    for (var candidate : candidates) {
      var score = similarityScore(normalizedQuestion, candidate.getNormalizedQuestion());
      if (score > bestScore) {
        bestScore = score;
        bestCandidate = candidate;
      }
    }

    return bestScore >= 0.97d ? bestCandidate : null;
  }

  private AssistantCacheEntry findReusableCachedEntry(
      ItEntity selectedIt,
      String normalizedSector,
      AssistantIntent intent,
      String normalizedQuestion,
      String documentVersion) {
    var exactAnyVersion = this.assistantCacheRepository
        .findFirstByItIdAndSetorAndIntentAndNormalizedQuestionOrderByUpdatedAtDesc(
            selectedIt.getId(),
            normalizedSector,
            intent.name(),
            normalizedQuestion)
        .orElse(null);

    if (exactAnyVersion != null) {
      return exactAnyVersion;
    }

    var candidates = this.assistantCacheRepository
        .findTop100ByItIdAndSetorAndIntentOrderByUpdatedAtDesc(
            selectedIt.getId(),
            normalizedSector,
            intent.name());

    AssistantCacheEntry bestCandidate = null;
    double bestScore = 0d;
    for (var candidate : candidates) {
      var score = similarityScore(normalizedQuestion, candidate.getNormalizedQuestion());
      if (score > bestScore) {
        bestScore = score;
        bestCandidate = candidate;
      }
    }

    if (bestScore < 0.97d || bestCandidate == null) {
      return null;
    }

    if (documentVersion.equals(bestCandidate.getDocumentVersion())) {
      return bestCandidate;
    }

    return bestCandidate;
  }

  private double similarityScore(String left, String right) {
    var normalizedLeft = this.intentDetector.normalize(left);
    var normalizedRight = this.intentDetector.normalize(right);

    if (normalizedLeft.equals(normalizedRight)) {
      return 1d;
    }

    var leftTokens = meaningfulTokens(normalizedLeft);
    var rightTokens = meaningfulTokens(normalizedRight);
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
      return 0d;
    }

    var intersectionCount = countMeaningfulIntersection(leftTokens, rightTokens);

    var overlapByMax = (double) intersectionCount / Math.max(leftTokens.size(), rightTokens.size());
    var overlapByMin = (double) intersectionCount / Math.min(leftTokens.size(), rightTokens.size());

    if (leftTokens.size() == rightTokens.size() && intersectionCount == leftTokens.size()) {
      return 0.995d;
    }

    if (intersectionCount >= 2 && overlapByMin >= 1d) {
      return 0.98d;
    }

    var containmentBonus = normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)
        ? 0.08d
        : 0d;

    return (overlapByMax * 0.6d) + (overlapByMin * 0.4d) + containmentBonus;
  }

  private Set<String> meaningfulTokens(String value) {
    return this.intentDetector.tokenize(value).stream()
        .map(token -> token.toLowerCase(Locale.ROOT))
        .filter(token -> token.length() >= 3)
        .filter(token -> !DOCUMENT_QUERY_STOPWORDS.contains(token))
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private static final Set<String> DOCUMENT_QUERY_STOPWORDS = Set.of(
      "me",
      "fala",
      "fale",
      "sobre",
      "quero",
      "saber",
      "explique",
      "explica",
      "diz",
      "diga",
      "mostrar",
      "mostre",
      "procure",
      "buscar",
      "busca",
      "consulte",
      "consulta",
      "qual",
      "quais",
      "que",
      "essa",
      "esse",
      "isso",
      "nesta",
      "nessa",
      "neste",
      "nesse",
      "desta",
      "dessa",
      "deste",
      "desse",
      "it",
      "pdf",
      "documento",
      "pagina",
      "passo",
      "por",
      "para",
      "com",
      "dos",
      "das",
      "do",
      "da",
      "de",
      "no",
      "na",
      "nos",
      "nas",
      "um",
      "uma",
      "uns",
      "umas",
      "the");

  private int countMeaningfulIntersection(Set<String> leftTokens, Set<String> rightTokens) {
    var remaining = new java.util.ArrayList<>(rightTokens);
    int matches = 0;

    for (var left : leftTokens) {
      for (int index = 0; index < remaining.size(); index += 1) {
        var right = remaining.get(index);
        if (tokensLookEquivalent(left, right)) {
          matches += 1;
          remaining.remove(index);
          break;
        }
      }
    }

    return matches;
  }

  private boolean tokensLookEquivalent(String left, String right) {
    if (left.equals(right)) {
      return true;
    }

    if (Math.min(left.length(), right.length()) >= 4
        && (left.startsWith(right) || right.startsWith(left))) {
      return true;
    }

    if (Math.min(left.length(), right.length()) >= 4) {
      return levenshteinDistance(left, right) <= 1;
    }

    return false;
  }

  private int levenshteinDistance(String left, String right) {
    int[] previous = new int[right.length() + 1];
    int[] current = new int[right.length() + 1];

    for (int j = 0; j <= right.length(); j += 1) {
      previous[j] = j;
    }

    for (int i = 1; i <= left.length(); i += 1) {
      current[0] = i;
      for (int j = 1; j <= right.length(); j += 1) {
        int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(
            Math.min(current[j - 1] + 1, previous[j] + 1),
            previous[j - 1] + cost);
      }

      int[] swap = previous;
      previous = current;
      current = swap;
    }

    return previous[right.length()];
  }
}
