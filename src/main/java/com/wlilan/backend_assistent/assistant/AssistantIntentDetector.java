package com.wlilan.backend_assistent.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.wlilan.backend_assistent.assistant.model.AssistantIntent;

@Component
public class AssistantIntentDetector {

  private static final Pattern STEP_HINT_PATTERN = Pattern.compile("\\bpasso\\s*(\\d{1,2})\\b");
  private static final Set<String> GREETING_HINTS = Set.of(
      "oi", "ola", "bom dia", "boa tarde", "boa noite", "e ai");
  private static final Set<String> HELP_HINTS = Set.of(
      "ajuda", "me ajuda", "me ajude", "socorro", "duvida", "como voce pode ajudar");
  private static final Set<String> CLARIFICATION_HINTS = Set.of(
      "nao entendi", "explica", "explique", "explica melhor", "detalha", "detalhe", "resume", "resuma");
  private static final Set<String> FOLLOW_UP_HINTS = Set.of(
      "sim",
      "isso",
      "isso mesmo",
      "pode continuar",
      "continua",
      "continue",
      "fala mais",
      "me fala mais",
      "me fala o que diz na it",
      "me fala que diz na it",
      "o que diz na it",
      "o que fala na it",
      "me diga o que diz na it");
  private static final Set<String> FOLLOW_UP_SECTION_HINTS = Set.of(
      "cuidado",
      "cuidados",
      "cuidados especiais",
      "como fazer",
      "o que fazer",
      "fonte",
      "pagina",
      "passo",
      "acao",
      "acoes",
      "possiveis causas",
      "causas");
  private static final Set<String> EXPLICIT_DOCUMENT_HINTS = Set.of(
      "na it",
      "nessa it",
      "dessa it",
      "desta it",
      "no documento",
      "nesse documento",
      "deste documento",
      "do documento",
      "no pdf",
      "nesse pdf",
      "deste pdf",
      "desse pdf",
      "passo",
      "pagina",
      "fonte");

  public AssistantIntent detect(String message) {
    var normalized = normalize(message);
    var tokens = tokenize(normalized);

    if (!hasText(normalized)) {
      return AssistantIntent.GREETING;
    }

    if (matchesAnyPhrase(normalized, GREETING_HINTS) && tokens.size() <= 6) {
      return AssistantIntent.GREETING;
    }

    if (matchesAnyPhrase(normalized, HELP_HINTS) && tokens.size() <= 8) {
      return AssistantIntent.HELP;
    }

    if (matchesAnyPhrase(normalized, CLARIFICATION_HINTS) && tokens.size() <= 8) {
      return AssistantIntent.CLARIFICATION;
    }

    if (matchesAnyPhrase(normalized, Set.of("essa it", "dessa it", "me ajuda com essa it", "quero saber dessa it"))) {
      return AssistantIntent.HELP;
    }

    if (isLowSignalFollowUp(normalized)) {
      return AssistantIntent.CLARIFICATION;
    }

    if (extractStepHint(message) != null || hasExplicitDocumentIntent(normalized)) {
      return AssistantIntent.DOCUMENT_QUERY;
    }

    return AssistantIntent.GENERAL;
  }

  public Integer extractStepHint(String message) {
    var matcher = STEP_HINT_PATTERN.matcher(normalize(message));
    if (!matcher.find()) {
      return null;
    }
    try {
      return Integer.valueOf(matcher.group(1));
    } catch (Exception exception) {
      return null;
    }
  }

  public String normalize(String value) {
    var raw = AssistantTextSanitizer.sanitize(value);
    var withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "");
    return withoutAccents
        .toLowerCase(Locale.ROOT)
        .replaceAll("[\\r\\n\\t]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public List<String> tokenize(String value) {
    return List.of(normalize(value).split(" ")).stream()
        .map(token -> token.replaceAll("^[^\\p{Alnum}]+|[^\\p{Alnum}%/-]+$", "").trim())
        .filter(token -> !token.isBlank())
        .toList();
  }

  public boolean isLowSignalFollowUp(String message) {
    var normalized = normalize(message);
    if (!hasText(normalized)) {
      return false;
    }

    if (matchesAnyPhrase(normalized, FOLLOW_UP_HINTS)) {
      return true;
    }

    if (referencesStructuredSection(normalized)) {
      return true;
    }

    var tokens = tokenize(normalized);
    if (tokens.size() <= 2 && tokens.stream().allMatch(token ->
        Set.of("sim", "isso", "ok", "ta", "certo", "beleza").contains(token))) {
      return true;
    }

    return tokens.size() <= 6
        && (normalized.contains("na it")
            || normalized.contains("nessa it")
            || normalized.contains("dessa it"));
  }

  private boolean referencesStructuredSection(String normalized) {
    if (!hasText(normalized)) {
      return false;
    }

    if (matchesAnyPhrase(normalized, FOLLOW_UP_SECTION_HINTS)) {
      return true;
    }

    return normalized.startsWith("e o ")
        || normalized.startsWith("e os ")
        || normalized.startsWith("e a ")
        || normalized.startsWith("e as ");
  }

  private boolean matchesAnyPhrase(String normalized, Set<String> phrases) {
    return phrases.stream().map(this::normalize).anyMatch(normalized::contains);
  }

  private boolean hasExplicitDocumentIntent(String normalized) {
    if (!hasText(normalized)) {
      return false;
    }

    if (matchesAnyPhrase(normalized, EXPLICIT_DOCUMENT_HINTS)) {
      return true;
    }

    return normalized.startsWith("me fale sobre a it")
        || normalized.startsWith("me fala sobre a it")
        || normalized.startsWith("o que a it")
        || normalized.startsWith("o que essa it")
        || normalized.startsWith("o que esse documento")
        || normalized.startsWith("consulte a it")
        || normalized.startsWith("consulta a it");
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }
}
