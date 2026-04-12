package com.wlilan.backend_assistent.assistant;

import java.util.UUID;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantAskResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantContextResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantOptionItem;
import com.wlilan.backend_assistent.assistant.dto.AssistantOptionsResponse;
import com.wlilan.backend_assistent.assistant.model.AssistantIntent;
import com.wlilan.backend_assistent.assistant.model.AssistantResponseMode;
import com.wlilan.backend_assistent.it.it.usecases.GetItByIdUseCase;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;

@Service
public class AssistantService {

  private final GetItByIdUseCase getItByIdUseCase;
  private final AssistantIntentDetector assistantIntentDetector;
  private final AssistantIndexSearcher assistantIndexSearcher;
  private final AssistantResponseFormatter assistantResponseFormatter;
  private final AssistantGeminiClient assistantGeminiClient;
  private final AssistantCacheService assistantCacheService;

  public AssistantService(
      GetItByIdUseCase getItByIdUseCase,
      AssistantIntentDetector assistantIntentDetector,
      AssistantIndexSearcher assistantIndexSearcher,
      AssistantResponseFormatter assistantResponseFormatter,
      AssistantGeminiClient assistantGeminiClient,
      AssistantCacheService assistantCacheService) {
    this.getItByIdUseCase = getItByIdUseCase;
    this.assistantIntentDetector = assistantIntentDetector;
    this.assistantIndexSearcher = assistantIndexSearcher;
    this.assistantResponseFormatter = assistantResponseFormatter;
    this.assistantGeminiClient = assistantGeminiClient;
    this.assistantCacheService = assistantCacheService;
  }

  public AssistantAskResponse ask(AssistantAskRequest request, UsuarioEntity usuario) {
    try {
      var setorAtivo = firstNonBlank(request.setorAtivo(), usuario.getSetorAtivo(), usuario.getSetor());
      var selectedIt = this.getItByIdUseCase.execute(parseUuid(request.itId()), setorAtivo);
      var intent = this.assistantIntentDetector.detect(request.message());
      intent = maybePromoteToDocumentQuery(selectedIt, request, intent);
      intent = maybeContinueWithinSelectedIt(request, intent);
      var responseMode = resolveResponseMode(intent);
      var normalizedQuestion = buildNormalizedQuestion(request, intent);
      var documentVersion = this.assistantCacheService.resolveDocumentVersion(selectedIt);
      var cacheModelKey = this.assistantCacheService.buildCacheModelKey(
          responseMode,
          intent);
      var useDatabaseCache = shouldUseDatabaseCache(responseMode, request);

      if (useDatabaseCache) {
        var cachedResponse = this.assistantCacheService.findCachedResponse(
            selectedIt,
            setorAtivo,
            intent,
            normalizedQuestion,
            documentVersion,
            cacheModelKey);
        if (cachedResponse != null) {
          return cachedResponse;
        }
      }

      AssistantAskResponse response;
      if (responseMode == AssistantResponseMode.CONVERSATION) {
        response = buildConversationResponse(selectedIt, request, intent);
      } else {
        response = buildDocumentGroundedResponse(selectedIt, request, intent);
      }

      if (useDatabaseCache && shouldCacheResponse(response)) {
        this.assistantCacheService.saveResponse(
            selectedIt,
            setorAtivo,
            intent,
            normalizedQuestion,
            documentVersion,
            cacheModelKey,
            request,
            response.message());
      }
      return response;
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      return AssistantAskResponse.builder()
          .message("Nao encontrei esse ponto com seguranca na IT selecionada. Se quiser, eu posso tentar outra busca com mais termos ou voce pode escolher um passo da IT.")
          .sourceType("assistant_safe_fallback")
          .warnings(java.util.List.of("O assistente nao conseguiu concluir essa consulta com seguranca."))
          .evidence(java.util.List.of())
          .metadata(java.util.Map.of(
              "mode", "safe_fallback",
              "cacheHit", false))
          .build();
    }
  }

  public AssistantContextResponse context(String itId, String setorAtivoRaw, UsuarioEntity usuario) {
    var setorAtivo = firstNonBlank(setorAtivoRaw, usuario.getSetorAtivo(), usuario.getSetor());
    var selectedIt = this.getItByIdUseCase.execute(parseUuid(itId), setorAtivo);
    var index = this.assistantIndexSearcher.loadIndex(selectedIt);
    var opcoes = buildStepOptions(index);
    var anomalyCount = (int) this.assistantIndexSearcher.structuredEntries(index).stream()
        .filter(entry -> "anomaly".equalsIgnoreCase(firstNonBlank(entry.entryType)))
        .count();

    return AssistantContextResponse.builder()
        .itId(String.valueOf(selectedIt.getId()))
        .documento(selectedIt.getDocumento())
        .titulo(firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .status(selectedIt.getStatus())
        .setor(selectedIt.getSetor())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .conversationId("it-" + selectedIt.getId())
        .documentVersion(this.assistantCacheService.resolveDocumentVersion(selectedIt))
        .stepCount(opcoes.size())
        .anomalyCount(anomalyCount)
        .opcoes(opcoes)
        .sampleQuestions(buildSampleQuestions(selectedIt, opcoes, anomalyCount))
        .metadata(java.util.Map.of(
            "mode", "selected_it_context",
            "chatReady", true,
            "sourcePolicy", "selected_it_only"))
        .build();
  }

  public AssistantOptionsResponse options(String itId, String setorAtivoRaw, UsuarioEntity usuario) {
    var setorAtivo = firstNonBlank(setorAtivoRaw, usuario.getSetorAtivo(), usuario.getSetor());
    var selectedIt = this.getItByIdUseCase.execute(parseUuid(itId), setorAtivo);
    var index = this.assistantIndexSearcher.loadIndex(selectedIt);
    var opcoes = buildStepOptions(index);

    return AssistantOptionsResponse.builder()
        .documento(selectedIt.getDocumento())
        .titulo(firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento()))
        .opcoes(opcoes)
        .build();
  }

  private java.util.List<AssistantOptionItem> buildStepOptions(com.wlilan.backend_assistent.assistant.model.ItIndex index) {
    return this.assistantIndexSearcher.structuredEntries(index).stream()
        .filter(entry -> "step".equalsIgnoreCase(firstNonBlank(entry.entryType)))
        .filter(entry -> entry.step != null)
        .sorted(Comparator
            .comparing((com.wlilan.backend_assistent.assistant.model.ItIndexEntry entry) -> entry.step == null ? Integer.MAX_VALUE : entry.step)
            .thenComparing(entry -> entry.page == null ? Integer.MAX_VALUE : entry.page))
        .collect(Collectors.groupingBy(
            entry -> entry.step,
            java.util.LinkedHashMap::new,
            Collectors.toList()))
        .entrySet().stream()
        .map(group -> this.assistantIndexSearcher.findBestEntryForStep(index, group.getKey(), null, null)
            .orElse(group.getValue().get(0)))
        .map(entry -> new AssistantOptionItem(
            entry.step,
            entry.page,
            this.assistantIndexSearcher.buildStepOptionTitle(index, entry.step, entry.page, entry.what)))
        .filter(option -> hasText(option.titulo()))
        .limit(20)
        .toList();
  }

  private java.util.List<String> buildSampleQuestions(
      com.wlilan.backend_assistent.it.ItEntity selectedIt,
      java.util.List<AssistantOptionItem> opcoes,
      int anomalyCount) {
    var samples = new java.util.ArrayList<String>();
    samples.add("Resuma os principais cuidados desta IT.");
    samples.add("Quais anomalias esta IT descreve?");
    if (!opcoes.isEmpty() && opcoes.get(0).passo() != null) {
      samples.add("Me explique o passo " + opcoes.get(0).passo() + ".");
    } else {
      samples.add("Quais são os passos principais desta IT?");
    }
    if (anomalyCount > 0) {
      samples.add("O que fazer quando houver anomalia nesta IT?");
    }
    samples.add("O que esta IT fala sobre " + firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento()) + "?");
    return samples.stream().distinct().limit(4).toList();
  }

  private AssistantIntent resolveResponseIntent(
      AssistantIntent detectedIntent,
      java.util.List<com.wlilan.backend_assistent.assistant.model.RankedEntry> matches) {
    if (detectedIntent == AssistantIntent.DOCUMENT_QUERY) {
      if (matches.isEmpty()) {
        return AssistantIntent.GENERAL;
      }
      var topEntryType = firstNonBlank(matches.get(0).entry().entryType);
      if ("anomaly".equalsIgnoreCase(topEntryType)) {
        return AssistantIntent.ANOMALY;
      }
      if ("step".equalsIgnoreCase(topEntryType)) {
        return AssistantIntent.OPERATION;
      }
      return AssistantIntent.GENERAL;
    }
    if (detectedIntent == AssistantIntent.OPERATION
        || detectedIntent == AssistantIntent.ANOMALY
        || matches.isEmpty()) {
      return detectedIntent;
    }

    var topEntryType = firstNonBlank(matches.get(0).entry().entryType);
    if ("anomaly".equalsIgnoreCase(topEntryType)) {
      return AssistantIntent.ANOMALY;
    }
    if ("step".equalsIgnoreCase(topEntryType)) {
      return AssistantIntent.OPERATION;
    }
    return detectedIntent;
  }

  private AssistantResponseMode resolveResponseMode(AssistantIntent intent) {
    return switch (intent) {
      case GREETING, HELP, CLARIFICATION, GENERAL -> AssistantResponseMode.CONVERSATION;
      default -> AssistantResponseMode.DOCUMENT_GROUNDED;
    };
  }

  private AssistantIntent maybePromoteToDocumentQuery(
      com.wlilan.backend_assistent.it.ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent detectedIntent) {
    if (detectedIntent == AssistantIntent.DOCUMENT_QUERY
        || detectedIntent == AssistantIntent.OPERATION
        || detectedIntent == AssistantIntent.ANOMALY
        || detectedIntent == AssistantIntent.GREETING) {
      return detectedIntent;
    }

    var index = this.assistantIndexSearcher.loadIndex(selectedIt);
    var matches = this.assistantIndexSearcher.findTopMatches(index, selectedIt, request, AssistantIntent.DOCUMENT_QUERY);
    if (shouldPromoteToSelectedIt(request, detectedIntent, matches)) {
      return AssistantIntent.DOCUMENT_QUERY;
    }

    return detectedIntent;
  }

  private AssistantIntent maybeContinueWithinSelectedIt(
      AssistantAskRequest request,
      AssistantIntent detectedIntent) {
    if (request.selectedStep() != null || request.selectedOptionTitle() != null) {
      return AssistantIntent.DOCUMENT_QUERY;
    }
    return detectedIntent;
  }

  private AssistantAskResponse buildConversationResponse(
      com.wlilan.backend_assistent.it.ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent intent) {
    var messages = this.assistantResponseFormatter.buildConversationMessages(selectedIt, request, intent);
    try {
      var answer = this.assistantGeminiClient.chat(messages, 0.7d);
      return this.assistantResponseFormatter.buildConversationResponse(
          selectedIt,
          request,
          intent,
          answer,
          this.assistantGeminiClient.getPrimaryModel());
    } catch (IllegalArgumentException exception) {
      return this.assistantResponseFormatter.buildConversationUnavailableResponse(
          selectedIt,
          request,
          intent,
          exception.getMessage(),
          this.assistantGeminiClient.getPrimaryModel());
    }
  }

  private AssistantAskResponse buildDocumentGroundedResponse(
      com.wlilan.backend_assistent.it.ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent intent) {
    var effectiveModel = this.assistantGeminiClient.getPrimaryModel();
    var index = this.assistantIndexSearcher.loadIndex(selectedIt);
    var documentInfoResponse = buildDocumentInfoResponseIfApplicable(selectedIt, request, index);
    if (documentInfoResponse != null) {
      return documentInfoResponse;
    }
    var matches = request.selectedStep() != null
        ? this.assistantIndexSearcher.findExactOptionMatches(index, request)
        : this.assistantIndexSearcher.findTopMatches(index, selectedIt, request, intent);
    var responseIntent = resolveResponseIntent(intent, matches);

    if (matches.isEmpty()) {
      var fallback = "Nao encontrei esse ponto de forma clara na IT selecionada. Se quiser, eu posso procurar por outro termo, resumir a IT ou te ajudar a refinar a pergunta.";
      return this.assistantResponseFormatter.buildDocumentGroundedNoEvidenceResponse(
          selectedIt,
          request,
          responseIntent,
          fallback,
          effectiveModel);
    }

    if (shouldUseFastGroundedResponse(request, matches)) {
      return this.assistantResponseFormatter.buildFastDocumentGroundedResponse(
          selectedIt,
          request,
          matches,
          index,
          responseIntent,
          "local_grounded_fastpath");
    }

    try {
      var messages = this.assistantResponseFormatter.buildDocumentGroundedMessages(
          selectedIt,
          request,
          matches);
      var answer = this.assistantGeminiClient.chat(messages, 0.35d);
      return this.assistantResponseFormatter.buildDocumentGroundedResponse(
          selectedIt,
          request,
          matches,
          responseIntent,
          answer,
          this.assistantGeminiClient.getPrimaryModel());
    } catch (IllegalArgumentException geminiException) {
      return this.assistantResponseFormatter.buildStructuredResponse(
          selectedIt,
          request,
          matches,
          index,
          responseIntent);
    }
  }

  private AssistantAskResponse buildDocumentInfoResponseIfApplicable(
      com.wlilan.backend_assistent.it.ItEntity selectedIt,
      AssistantAskRequest request,
      com.wlilan.backend_assistent.assistant.model.ItIndex index) {
    var normalized = this.assistantIntentDetector.normalize(request.message());
    if (!hasText(normalized)) {
      return null;
    }

    if (containsAny(normalized, "revisao", "revisao da it", "qual e a revisao")) {
      var body = "A revisao atual da IT e " + firstNonBlank(selectedIt.getRevisao(), "nao informada") + ".";
      return this.assistantResponseFormatter.buildDocumentInfoResponse(
          selectedIt,
          request,
          "Revisao da IT",
          body,
          null,
          "document_info_revision");
    }

    if (containsAny(normalized, "titulo", "titulo da it", "nome da it")) {
      var body = "O titulo da IT e " + firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento(), "nao informado") + ".";
      return this.assistantResponseFormatter.buildDocumentInfoResponse(
          selectedIt,
          request,
          "Titulo da IT",
          body,
          null,
          "document_info_title");
    }

    if (containsAny(normalized, "autor", "autorizador", "data de publicacao", "data de criacao", "data de impressao")) {
      var metadataLines = new java.util.ArrayList<String>();
      if (hasText(selectedIt.getRevisao())) {
        metadataLines.add("- Revisao: " + selectedIt.getRevisao());
      }
      if (selectedIt.getDataPublicacao() != null) {
        metadataLines.add("- Data de publicacao: " + selectedIt.getDataPublicacao().toLocalDate());
      }
      var firstEntry = index.entries.stream().findFirst().orElse(null);
      if (firstEntry != null) {
        if (hasText(firstEntry.author)) {
          metadataLines.add("- Autor: " + firstEntry.author.trim());
        }
        if (hasText(firstEntry.authorizer)) {
          metadataLines.add("- Autorizador: " + firstEntry.authorizer.trim());
        }
        if (hasText(firstEntry.createDate)) {
          metadataLines.add("- Data de criacao: " + firstEntry.createDate.trim());
        }
        if (hasText(firstEntry.printDate)) {
          metadataLines.add("- Data de impressao: " + firstEntry.printDate.trim());
        }
      }

      if (!metadataLines.isEmpty()) {
        return this.assistantResponseFormatter.buildDocumentInfoResponse(
            selectedIt,
            request,
            "Informacoes do documento",
            String.join("\n", metadataLines),
            null,
            "document_info_metadata");
      }
    }

    var sectionKeywords = List.of(
        "resultados esperados",
        "referencias",
        "anexos",
        "definicoes",
        "simbolos e abreviaturas",
        "abreviaturas",
        "simbolos",
        "recursos necessarios");

    for (var keyword : sectionKeywords) {
      if (!normalized.contains(keyword)) {
        continue;
      }

      var sectionEntry = findBestSectionEntry(index, keyword);
      if (sectionEntry != null) {
        var content = firstNonBlank(
            formatSectionContent(sectionEntry),
            "Encontrei a secao na IT, mas o conteudo estruturado dela esta incompleto.");
        return this.assistantResponseFormatter.buildDocumentInfoResponse(
            selectedIt,
            request,
            firstNonBlank(sectionEntry.sectionTitle, keyword),
            content,
            sectionEntry.page,
            "document_info_section");
      }
    }

    return null;
  }

  private com.wlilan.backend_assistent.assistant.model.ItIndexEntry findBestSectionEntry(
      com.wlilan.backend_assistent.assistant.model.ItIndex index,
      String keyword) {
    var normalizedKeyword = this.assistantIntentDetector.normalize(keyword);
    return index.entries.stream()
        .filter(entry -> "section".equalsIgnoreCase(firstNonBlank(entry.entryType)))
        .filter(entry -> {
          var sectionTitle = this.assistantIntentDetector.normalize(firstNonBlank(entry.sectionTitle, entry.what));
          return hasText(sectionTitle) && sectionTitle.contains(normalizedKeyword);
        })
        .max(Comparator
            .comparingInt((com.wlilan.backend_assistent.assistant.model.ItIndexEntry entry) ->
                this.assistantIntentDetector.normalize(firstNonBlank(entry.sectionTitle, entry.what)).equals(normalizedKeyword) ? 10 : 0)
            .thenComparing(entry -> entry.page == null ? Integer.MAX_VALUE : -entry.page))
        .orElse(null);
  }

  private String formatSectionContent(com.wlilan.backend_assistent.assistant.model.ItIndexEntry entry) {
    var content = firstNonBlank(entry.how, entry.what, entry.care);
    if (!hasText(content)) {
      return "";
    }

    var sanitized = content
        .replace("\r", "\n")
        .replaceAll("\\s*\\n\\s*", "\n")
        .trim();

    if (sanitized.contains("•")) {
      return sanitized.replace("•", "-");
    }
    return sanitized;
  }

  private boolean containsAny(String text, String... snippets) {
    for (var snippet : snippets) {
      if (text.contains(this.assistantIntentDetector.normalize(snippet))) {
        return true;
      }
    }
    return false;
  }

  private boolean shouldUseFastGroundedResponse(
      AssistantAskRequest request,
      java.util.List<com.wlilan.backend_assistent.assistant.model.RankedEntry> matches) {
    if (matches == null || matches.isEmpty()) {
      return false;
    }

    var top = matches.get(0);
    var secondScore = matches.size() > 1 ? matches.get(1).score() : 0d;
    var strongSingleMatch = matches.size() == 1 && top.score() >= 10d;
    var dominantTopMatch = top.score() >= 14d && (secondScore <= 0d || top.score() >= secondScore * 1.8d);
    var lowSignalFollowUp = this.assistantIntentDetector.isLowSignalFollowUp(request.message());
    var topEntryType = firstNonBlank(top.entry().entryType).toLowerCase(Locale.ROOT);
    var topLabel = this.assistantIntentDetector.normalize(firstNonBlank(top.entry().what, top.entry().sectionTitle));
    var normalizedMessage = this.assistantIntentDetector.normalize(firstNonBlank(request.message()));
    var exactLabelMatch = hasText(topLabel)
        && hasText(normalizedMessage)
        && (normalizedMessage.contains(topLabel) || topLabel.contains(normalizedMessage));

    if (lowSignalFollowUp) {
      return false;
    }

    if (request.selectedStep() != null) {
      return true;
    }

    if (!"step".equals(topEntryType) && !"anomaly".equals(topEntryType)) {
      return false;
    }

    return exactLabelMatch || strongSingleMatch || dominantTopMatch;
  }

  private boolean shouldPromoteToSelectedIt(
      AssistantAskRequest request,
      AssistantIntent detectedIntent,
      java.util.List<com.wlilan.backend_assistent.assistant.model.RankedEntry> matches) {
    if (matches == null || matches.isEmpty()) {
      return false;
    }

    var topScore = matches.get(0).score();
    if (detectedIntent == AssistantIntent.GENERAL) {
      var normalizedMessage = this.assistantIntentDetector.normalize(request.message());
      var substantiveTokenCount = this.assistantIntentDetector.tokenize(request.message()).stream()
          .filter(token -> token.length() >= 4)
          .count();

      return topScore >= 7.5d
          && (normalizedMessage.contains("sobre")
              || substantiveTokenCount >= 2
              || topScore >= 11d);
    }

    return topScore >= 8.5d;
  }

  private boolean shouldUseDatabaseCache(AssistantResponseMode responseMode, AssistantAskRequest request) {
    if (responseMode != AssistantResponseMode.DOCUMENT_GROUNDED) {
      return false;
    }

    return hasText(request.message()) || request.selectedStep() != null || hasText(request.selectedOptionTitle());
  }

  private boolean shouldCacheResponse(AssistantAskResponse response) {
    if (response == null) {
      return false;
    }

    var sourceType = firstNonBlank(response.sourceType());
    return !"conversation_provider_unavailable".equalsIgnoreCase(sourceType)
        && !"assistant_safe_fallback".equalsIgnoreCase(sourceType);
  }

  private UUID parseUuid(String raw) {
    try {
      return UUID.fromString(String.valueOf(raw).trim());
    } catch (Exception exception) {
      throw new IllegalArgumentException("Nao foi possivel identificar a IT selecionada para consulta.");
    }
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.trim().isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String buildNormalizedQuestion(AssistantAskRequest request, AssistantIntent intent) {
    if (request.selectedStep() != null) {
      var stepKey = buildStepCacheKey(request);
      if (hasText(stepKey)) {
        return stepKey;
      }
    }

    var base = firstNonBlank(request.selectedOptionTitle(), request.message());
    var builder = new StringBuilder(base);
    if (request.selectedStep() != null) {
      builder.append(" passo ").append(request.selectedStep());
    }
    if (request.selectedPage() != null) {
      builder.append(" pagina ").append(request.selectedPage());
    }

    if (this.assistantIntentDetector.isLowSignalFollowUp(request.message())
        && request.history() != null
        && !request.history().isEmpty()) {
      var recentContext = request.history().stream()
          .filter(turn -> turn != null && turn.content() != null && !turn.content().trim().isBlank())
          .skip(Math.max(0, request.history().size() - 3))
          .map(turn -> firstNonBlank(turn.role(), "user") + ":" + turn.content().trim())
          .collect(java.util.stream.Collectors.joining(" | "));
      if (!recentContext.isBlank()) {
        builder.append(" contexto ").append(recentContext);
      }
    }

    var normalized = this.assistantIntentDetector.normalize(builder.toString());
    if (intent == AssistantIntent.DOCUMENT_QUERY
        || intent == AssistantIntent.OPERATION
        || intent == AssistantIntent.ANOMALY) {
      return canonicalizeDocumentQuestion(normalized);
    }
    return normalized;
  }

  private String buildStepCacheKey(AssistantAskRequest request) {
    var optionTitle = firstNonBlank(request.selectedOptionTitle(), request.message());
    var normalizedTitle = this.assistantIntentDetector.normalize(optionTitle)
        .replaceAll("^passo\\s*\\d{1,2}\\s*[:.-]?\\s*", "")
        .replaceAll("^\\d{1,2}\\s*[:.-]?\\s*", "")
        .trim();

    var meaningfulTokens = this.assistantIntentDetector.tokenize(normalizedTitle).stream()
        .map(token -> token.toLowerCase(Locale.ROOT))
        .filter(token -> token.length() >= 3)
        .filter(token -> !DOCUMENT_QUERY_STOPWORDS.contains(token))
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (!meaningfulTokens.isEmpty()) {
      return String.join(" ", meaningfulTokens);
    }

    return "passo " + request.selectedStep();
  }

  private String canonicalizeDocumentQuestion(String normalized) {
    var sanitized = this.assistantIntentDetector.normalize(normalized)
        .replaceAll("^(me\\s+fala\\s+sobre|me\\s+fale\\s+sobre|me\\s+explica|me\\s+explique|o\\s+que\\s+a\\s+it\\s+diz\\s+sobre|o\\s+que\\s+essa\\s+it\\s+diz\\s+sobre|o\\s+que\\s+a\\s+it\\s+fala\\s+sobre|o\\s+que\\s+essa\\s+it\\s+fala\\s+sobre|quero\\s+saber\\s+sobre|fale\\s+sobre|fala\\s+sobre|sobre)\\s+", "")
        .trim();

    var meaningfulTokens = this.assistantIntentDetector.tokenize(sanitized).stream()
        .map(token -> token.toLowerCase(Locale.ROOT))
        .filter(token -> token.length() >= 3)
        .filter(token -> !DOCUMENT_QUERY_STOPWORDS.contains(token))
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (meaningfulTokens.isEmpty()) {
      return sanitized;
    }

    return String.join(" ", meaningfulTokens);
  }

  private static final java.util.Set<String> DOCUMENT_QUERY_STOPWORDS = java.util.Set.of(
      "me",
      "fala",
      "fale",
      "sobre",
      "quero",
      "saber",
      "explica",
      "explique",
      "diz",
      "diga",
      "mostra",
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
      "umas");
}
