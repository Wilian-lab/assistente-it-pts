package com.wlilan.backend_assistent.assistant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantAskResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantEvidenceItem;
import com.wlilan.backend_assistent.assistant.model.AssistantIntent;
import com.wlilan.backend_assistent.assistant.model.AssistantResponseMode;
import com.wlilan.backend_assistent.assistant.model.ItIndex;
import com.wlilan.backend_assistent.assistant.model.ItIndexEntry;
import com.wlilan.backend_assistent.assistant.model.OperationEnrichment;
import com.wlilan.backend_assistent.assistant.model.RankedEntry;
import com.wlilan.backend_assistent.it.ItEntity;

@Component
public class AssistantResponseFormatter {

  private static final List<String> OPERATION_SPLIT_MARKERS = List.of(
      " Verificar ",
      " Monitorar ",
      " Ajustar ",
      " Inspecionar se ",
      " Caso ",
      " Quando ");
  private static final Set<String> CARE_HINTS = Set.of(
      "sempre",
      "evitar",
      "nao ",
      "não ",
      "seguranca",
      "segurança",
      "cuidado",
      "cuidar",
      "bloqueio",
      "sinalizacao",
      "sinalização",
      "loto",
      "epi",
      "manter",
      "qualquer duvida",
      "qualquer dúvida");

  private static final Set<String> CARE_PREFIX_HINTS = Set.of(
      "seguindo",
      "procedimentos de seguranca",
      "realizar o bloqueio",
      "cuidar para",
      "bloqueio e sinalizacao");

  private final AssistantIntentDetector intentDetector;

  public AssistantResponseFormatter(AssistantIntentDetector intentDetector) {
    this.intentDetector = intentDetector;
  }

  public AssistantAskResponse buildStructuredResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent) {
    var deterministicAnswer = formatStructuredResponse(selectedIt, matches, index, intent);
    var primaryEntry = matches.get(0).entry();
    return AssistantAskResponse.builder()
        .message(deterministicAnswer)
        .sourceType("it_structured")
        .documento(resolveDocumentCode(selectedIt, request, matches))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of())
        .evidence(buildEvidence(matches))
        .metadata(Map.of(
            "mode", "structured_step_response",
            "results", matches.size(),
            "intent", intent.name().toLowerCase(Locale.ROOT),
            "primaryPage", primaryEntry.page == null ? "-" : primaryEntry.page,
            "cacheHit", false))
        .build();
  }

  public AssistantAskResponse buildDocumentGroundedResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      AssistantIntent intent,
      String answer,
      String model) {
    var primaryEntry = matches.get(0).entry();
    return AssistantAskResponse.builder()
        .message(answer.trim())
        .sourceType("gemini_it")
        .documento(resolveDocumentCode(selectedIt, request, matches))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of())
        .evidence(buildEvidence(matches))
        .metadata(Map.of(
            "mode", "gemini_indexed_context",
            "results", matches.size(),
            "model", model,
            "intent", intent.name().toLowerCase(Locale.ROOT),
            "responseMode", AssistantResponseMode.DOCUMENT_GROUNDED.name().toLowerCase(Locale.ROOT),
            "primaryPage", primaryEntry.page == null ? "-" : primaryEntry.page,
            "cacheHit", false))
        .build();
  }

  public AssistantAskResponse buildDocumentGroundedNoEvidenceResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent intent,
      String answer,
      String model) {
    return AssistantAskResponse.builder()
        .message(firstNonBlank(answer))
        .sourceType("gemini_it")
        .documento(firstNonBlank(selectedIt.getDocumento(), request.documentCode()))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of("Nao encontrei um trecho especifico na IT para essa pergunta."))
        .evidence(List.of())
        .metadata(Map.of(
            "mode", "gemini_indexed_context",
            "results", 0,
            "model", model,
            "intent", intent.name().toLowerCase(Locale.ROOT),
            "responseMode", AssistantResponseMode.DOCUMENT_GROUNDED.name().toLowerCase(Locale.ROOT),
            "cacheHit", false))
        .build();
  }

  public AssistantAskResponse buildFastDocumentGroundedResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent,
      String model) {
    String answer;
    try {
      answer = buildFastGroundedAnswer(selectedIt, request, matches, index, intent);
    } catch (Exception exception) {
      var entry = matches.get(0).entry();
      answer = buildMinimalGroundedAnswer(selectedIt, request, entry, matches);
    }

    return AssistantAskResponse.builder()
        .message(answer)
        .sourceType("it_grounded_fast")
        .documento(resolveDocumentCode(selectedIt, request, matches))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of())
        .evidence(buildEvidence(matches))
        .metadata(Map.of(
            "mode", "document_grounded_fastpath",
            "results", matches.size(),
            "model", model,
            "intent", intent.name().toLowerCase(Locale.ROOT),
            "responseMode", AssistantResponseMode.DOCUMENT_GROUNDED.name().toLowerCase(Locale.ROOT),
            "cacheHit", false))
        .build();
  }

  public AssistantAskResponse buildDocumentInfoResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      String title,
      String body,
      Integer page,
      String mode) {
    var lines = new ArrayList<String>();
    lines.add("**" + firstNonBlank(title, "Informacoes da IT") + "**");
    var intro = buildDocumentInfoIntro(selectedIt, title);
    if (hasText(intro)) {
      lines.add(intro);
      lines.add("");
    }
    lines.add(formatDocumentInfoBody(title, body));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildDocumentInfoSource(selectedIt, request, page));
    lines.add("");
    lines.add("Deseja saber algo mais?");

    return AssistantAskResponse.builder()
        .message(String.join("\n", lines))
        .sourceType("it_grounded_fast")
        .documento(firstNonBlank(selectedIt.getDocumento(), request.documentCode()))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of())
        .evidence(List.of())
        .metadata(Map.of(
            "mode", firstNonBlank(mode, "document_info_local"),
            "responseMode", AssistantResponseMode.DOCUMENT_GROUNDED.name().toLowerCase(Locale.ROOT),
            "cacheHit", false))
        .build();
  }

  public AssistantAskResponse buildConversationResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent intent,
      String answer,
      String model) {
    return AssistantAskResponse.builder()
        .message(firstNonBlank(answer))
        .sourceType("gemini_conversation")
        .documento(firstNonBlank(selectedIt.getDocumento(), request.documentCode()))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of())
        .evidence(List.of())
        .metadata(Map.of(
            "mode", "conversation",
            "model", model,
            "intent", intent.name().toLowerCase(Locale.ROOT),
            "responseMode", AssistantResponseMode.CONVERSATION.name().toLowerCase(Locale.ROOT),
            "cacheHit", false))
        .build();
  }

  public AssistantAskResponse buildConversationUnavailableResponse(
      ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent intent,
      String providerError,
      String model) {
    return AssistantAskResponse.builder()
        .message("Tive uma falha momentanea para responder agora. Me manda de novo em seguida que eu tento continuar a conversa normalmente.")
        .sourceType("conversation_provider_unavailable")
        .documento(firstNonBlank(selectedIt.getDocumento(), request.documentCode()))
        .titulo(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento()))
        .revisao(selectedIt.getRevisao())
        .downloadUrl("/it/" + selectedIt.getId() + "/file")
        .previewUrl("/it/" + selectedIt.getId() + "/file")
        .warnings(List.of(firstNonBlank(providerError, "Falha ao acessar o provedor conversacional.")))
        .evidence(List.of())
        .metadata(Map.of(
            "mode", "conversation_unavailable",
            "model", model,
            "intent", intent.name().toLowerCase(Locale.ROOT),
            "responseMode", AssistantResponseMode.CONVERSATION.name().toLowerCase(Locale.ROOT),
            "cacheHit", false))
        .build();
  }

  private String buildFastGroundedAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent) {
    var entry = matches.get(0).entry();
    var requestedSection = detectRequestedSection(request.message());
    if (requestedSection != ResponseSection.NONE) {
      return buildFocusedSectionAnswer(selectedIt, request, matches, index, intent, entry, requestedSection);
    }
    return switch (intent) {
      case OPERATION -> buildFastOperationAnswer(selectedIt, request, matches, index, entry);
      case ANOMALY -> buildFastAnomalyAnswer(entry);
      default -> buildFastGeneralAnswer(entry);
    };
  }

  public List<Map<String, String>> buildConversationMessages(
      ItEntity selectedIt,
      AssistantAskRequest request,
      AssistantIntent intent) {
    var systemPrompt = """
        Voce e um assistente conversacional geral.

        Nesta fase, seu papel e conversar de forma natural, util e fluida, como um assistente estilo ChatGPT, sem parecer uma mensagem automatica.
        Responda sempre em portugues do Brasil.
        Nunca responda em ingles.
        Nunca invente fatos especificos quando nao tiver base para isso.

        Existe uma IT selecionada no contexto atual, mas ela nao precisa ser o foco da conversa.
        So use a IT quando o usuario pedir explicitamente para consultar a IT, o documento, o PDF, um passo, uma pagina ou alguma informacao dele.
        Se o usuario quiser apenas conversar, converse normalmente.

        Regras:
        - converse naturalmente com o usuario;
        - nao force a conversa para a IT;
        - nao despeje automaticamente passos ou blocos tecnicos;
        - se o usuario so quiser bater papo, responda como um assistente normal;
        - se o usuario pedir ajuda, explique de forma humana e simples;
        - se o usuario pedir explicitamente para consultar a IT, voce pode usar a IT selecionada como base;
        - prefira respostas curtas e naturais por padrao.

        Objetivo:
        parecer uma IA real conversando, com liberdade para falar sobre qualquer assunto.
        """;

    var userPrompt = new StringBuilder();
    userPrompt.append("Mensagem do usuario: ").append(firstNonBlank(request.message(), "-")).append("\n");
    userPrompt.append("Intencao: ").append(intent.name().toLowerCase(Locale.ROOT)).append("\n");
    appendHistory(userPrompt, request);
    userPrompt.append("\nContexto opcional disponivel caso o usuario queira consultar a IT:\n");
    userPrompt.append("IT ativa: ").append(firstNonBlank(selectedIt.getDocumento(), request.documentCode(), "-")).append("\n");
    userPrompt.append("Titulo da IT: ").append(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), "-")).append("\n");
    userPrompt.append("\nResponda naturalmente, como em uma conversa real. Nao puxe a IT por conta propria se o usuario nao pedir.\n");

    return List.of(
        Map.of("role", "system", "content", systemPrompt.trim()),
        Map.of("role", "user", "content", userPrompt.toString().trim()));
  }

  public List<Map<String, String>> buildDocumentGroundedMessages(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches) {
    var systemPrompt = """
        Voce e um assistente conversacional de ITs.
        Responda sempre em portugues do Brasil.
        Responda de forma natural, clara e humana.
        Use a IT selecionada como referencia principal.
        Quando houver trechos recuperados da IT, use-os como base para responder, mas nao copie blocos automaticamente nem pareca um sistema engessado.
        Nunca invente equipamento, processo, linha, etapa, acao ou contexto que nao esteja claramente presente no material recuperado.
        Nunca complete frases truncadas, tabelas quebradas ou campos incompletos supondo uma continuacao que nao esta escrita.
        Se um trecho estiver incompleto ou ambiguo, deixe isso explicito em vez de adivinhar.
        Nunca interprete siglas, termos tecnicos, nomes de equipamentos ou palavras da IT com base no que voce acha que significam.
        Se a IT disser "Spin filtrado", "ICM", "Vetter" ou qualquer outro termo tecnico, mantenha o termo como esta em vez de explicar por conta propria o que ele seria.
        Nunca use expressoes como "imagino", "provavelmente", "deve ser", "parece ser" ou "pode significar".
        Se o contexto recuperado for insuficiente, diga isso com clareza.
        Se o termo perguntado nao aparecer ou nao puder ser confirmado pela IT, diga que nao encontrou esse ponto na IT selecionada.
        Leia os trechos recuperados e resuma apenas o que esta explicitamente escrito na IT para o usuario.
        Se houver mais de um trecho relevante, voce pode combinar as informacoes em uma resposta unica.
        Prefira respostas organizadas em partes curtas, com respiro entre blocos.
        Nao entregue tudo em um unico paragrafo longo e misturado.
        Quando ajudar, use titulos curtos em negrito como "**Resumo**", "**O que fazer**", "**Como fazer**", "**Cuidados**" e "**Fonte**".
        Se a pergunta for objetiva, responda de forma objetiva, mas ainda organizada.
        Encerre de forma natural, sem soar automatico.
        """;

    var userPrompt = new StringBuilder();
    userPrompt.append("Pergunta do usuario: ").append(firstNonBlank(request.message(), "-")).append("\n");
    userPrompt.append("IT ativa: ").append(firstNonBlank(selectedIt.getDocumento(), request.documentCode(), "-")).append("\n");
    userPrompt.append("Titulo da IT: ").append(firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), "-")).append("\n");
    appendHistory(userPrompt, request);
    userPrompt.append("\nContexto recuperado da IT:\n");

    if (matches == null || matches.isEmpty()) {
      userPrompt.append("- Nenhum trecho relevante foi localizado.\n");
    } else {
      for (int index = 0; index < Math.min(matches.size(), 2); index += 1) {
        var entry = matches.get(index).entry();
        userPrompt.append("\nTrecho ").append(index + 1).append("\n");
        userPrompt.append("Pagina: ").append(entry.page == null ? "-" : entry.page).append("\n");
        if (entry.step != null) {
          userPrompt.append("Passo: ").append(entry.step).append("\n");
        }
        userPrompt.append("Titulo: ").append(firstNonBlank(entry.sectionTitle, entry.what, "-")).append("\n");
        userPrompt.append("O que: ").append(firstNonBlank(entry.what, "-")).append("\n");
        userPrompt.append("Como: ").append(firstNonBlank(entry.how, "-")).append("\n");
        userPrompt.append("Cuidados: ").append(firstNonBlank(entry.care, "-")).append("\n");
        if (hasText(entry.possibleCauses)) {
          userPrompt.append("Possiveis causas: ").append(entry.possibleCauses.trim()).append("\n");
        }
        if (hasText(entry.actionText)) {
          userPrompt.append("Acao: ").append(entry.actionText.trim()).append("\n");
        }
      }
    }

    userPrompt.append("\nResponda em tom conversacional, mas resuma somente o que esta escrito na IT.\n");
    userPrompt.append("Nao explique o significado tecnico dos termos nem acrescente interpretacoes suas.\n");
    userPrompt.append("Organize a resposta em partes curtas e separadas quando isso melhorar a leitura.\n");
    userPrompt.append("Se houver acao, cuidado ou fonte, separe esses pontos visualmente em vez de misturar tudo em um bloco unico.\n");

    return List.of(
        Map.of("role", "system", "content", systemPrompt.trim()),
        Map.of("role", "user", "content", userPrompt.toString().trim()));
  }

  private void appendHistory(StringBuilder userPrompt, AssistantAskRequest request) {
    if (request.history() == null || request.history().isEmpty()) {
      return;
    }
    userPrompt.append("\nHistorico recente:\n");
    request.history().stream()
        .filter(turn -> turn != null && hasText(turn.content()))
        .limit(3)
        .forEach(turn -> userPrompt
            .append("- ")
            .append(firstNonBlank(turn.role(), "user"))
            .append(": ")
            .append(turn.content().trim())
            .append("\n"));
  }

  private java.util.List<AssistantEvidenceItem> buildEvidence(List<RankedEntry> matches) {
    return matches.stream()
        .limit(5)
        .map(RankedEntry::entry)
        .map(entry -> AssistantEvidenceItem.builder()
            .passo(entry.step)
            .pagina(entry.page)
            .entryType(firstNonBlank(entry.entryType))
            .sectionNumber(entry.sectionNumber)
            .sectionTitle(firstNonBlank(entry.sectionTitle))
            .what(firstNonBlank(entry.what))
            .how(firstNonBlank(entry.how))
            .care(firstNonBlank(entry.care))
            .possibleCauses(firstNonBlank(entry.possibleCauses))
            .actionText(firstNonBlank(entry.actionText))
            .build())
        .toList();
  }

  private String formatStructuredResponse(
      ItEntity selectedIt,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent) {
    var primaryEntry = matches.get(0).entry();
    var lines = new ArrayList<String>();
    lines.add(buildDocumentHeader(selectedIt, primaryEntry, matches));
    lines.add("");
    return switch (intent) {
      case ANOMALY -> String.join("\n", lines) + formatAnomalyBlocks(matches);
      case OPERATION -> String.join("\n", lines) + formatOperationBlocks(matches, index);
      default -> String.join("\n", lines) + formatGeneralBlocks(matches);
    };
  }

  private String formatOperationBlocks(List<RankedEntry> matches, ItIndex index) {
    var lines = new ArrayList<String>();
    for (int i = 0; i < matches.size(); i += 1) {
      var entry = matches.get(i).entry();
      var enrichment = enrichOperationEntry(entry, index);
      lines.add("**Bloco " + (i + 1) + "**");
      lines.add("Passo: " + (entry.step == null ? "-" : entry.step));
      lines.add("Pagina: " + (entry.page == null ? "-" : entry.page));
      lines.add("**O que fazer**");
      lines.add(firstNonBlank(enrichment.what(), "Nao identificado."));
      lines.add("");
      lines.add("**Como fazer**");
      lines.add(formatBulletBlock(enrichment.how(), "Nao identificado."));
      lines.add("");
      lines.add("**Cuidados especiais**");
      lines.add(formatBulletBlock(enrichment.care(), "Nao identificado claramente no trecho estruturado desta pagina."));
      lines.add("");
    }
    return String.join("\n", lines);
  }

  private String formatAnomalyBlocks(List<RankedEntry> matches) {
    var lines = new ArrayList<String>();
    for (int i = 0; i < matches.size(); i += 1) {
      var entry = matches.get(i).entry();
      lines.add("**Bloco " + (i + 1) + "**");
      lines.add("Anomalia: " + firstNonBlank(entry.what, "-"));
      lines.add("Passo: " + (entry.step == null ? "-" : entry.step));
      lines.add("Pagina: " + (entry.page == null ? "-" : entry.page));
      lines.add("**Possiveis causas**");
      lines.add(firstNonBlank(entry.possibleCauses, entry.what, "Nao identificado."));
      lines.add("");
      lines.add("**Acao**");
      lines.add(firstNonBlank(entry.actionText, entry.how, "Nao identificado."));
      lines.add("");
      lines.add("**Cuidados especiais**");
      lines.add(firstNonBlank(entry.care, "Nao identificado."));
      lines.add("");
    }
    return String.join("\n", lines);
  }

  private String formatGeneralBlocks(List<RankedEntry> matches) {
    var lines = new ArrayList<String>();
    for (int i = 0; i < matches.size(); i += 1) {
      var entry = matches.get(i).entry();
      lines.add("**Bloco " + (i + 1) + "**");
      lines.add("Pagina: " + (entry.page == null ? "-" : entry.page));
      if (entry.step != null) {
        lines.add("Passo: " + entry.step);
      }
      if (hasText(entry.sectionTitle)) {
        lines.add("Titulo do trecho: " + entry.sectionTitle.trim());
      } else if (hasText(entry.what)) {
        lines.add("Titulo do trecho: " + cleanGeneralLine(entry.what));
      }
      lines.add("**Trecho literal**");
      lines.add(firstNonBlank(
          formatGeneralExcerpt(entry),
          "Nao consta trecho literal suficiente no bloco encontrado."));
      if (hasText(entry.care)) {
        lines.add("");
        lines.add("**Cuidados especiais**");
        lines.add(formatBulletBlock(entry.care, "Nao consta no bloco."));
      }
      lines.add("");
      lines.add("**Fonte**");
      lines.add(buildEntrySource(entry));
      lines.add("");
    }
    return String.join("\n", lines);
  }

  private String formatGeneralExcerpt(ItIndexEntry entry) {
    var parts = new ArrayList<String>();
    if (hasText(entry.what) && !looksLikeDuplicatedHeading(entry.what, entry.sectionTitle)) {
      parts.add(cleanGeneralLine(entry.what));
    }
    if (hasText(entry.how)) {
      parts.add(cleanGeneralParagraph(entry.how));
    }
    if (parts.isEmpty() && hasText(entry.care)) {
      parts.add(cleanGeneralParagraph(entry.care));
    }
    return String.join("\n\n", parts).trim();
  }

  private String buildFastOperationAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      ItIndexEntry entry) {
    var enrichment = enrichOperationEntry(entry, index);
    var operationContext = extractOperationContext(entry, enrichment);
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(enrichment.what(), entry.what)));
    lines.add("");
    lines.add("**Contexto da IT**");
    lines.add(buildOperationContextBlock(selectedIt, entry));
    if (hasText(operationContext)) {
      lines.add("");
      lines.add("**Resumo**");
      lines.add(operationContext);
    }
    lines.add("");
    lines.add("**O que fazer**");
    lines.add(firstNonBlank(enrichment.what(), cleanGeneralLine(entry.what), "Nao identificado."));
    lines.add("");
    lines.add("**" + resolveOperationDetailHeading(enrichment, entry) + "**");
    lines.add(formatBulletBlock(
        firstNonBlank(enrichment.how(), entry.how, entry.actionText),
        "Nao consegui localizar um passo a passo claro nesse trecho da IT."));

    var careText = firstNonBlank(enrichment.care(), entry.care);
    if (hasText(careText)) {
      lines.add("");
      lines.add("**Cuidados especiais**");
      lines.add(formatBulletBlock(careText, "Nao identificado."));
    }

    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildCompactSource(selectedIt, request, matches));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildOperationContextBlock(ItEntity selectedIt, ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add("- Titulo: " + firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento(), entry.documentTitle, "IT selecionada"));
    lines.add("- Documento: " + firstNonBlank(selectedIt.getDocumento(), entry.documentCode, "-"));
    if (hasReliableRevision(selectedIt.getRevisao())) {
      lines.add("- Revisao: " + selectedIt.getRevisao());
    }
    if (selectedIt.getDataPublicacao() != null) {
      lines.add("- Data de publicacao: " + selectedIt.getDataPublicacao().toLocalDate());
    }
    if (entry.step != null) {
      lines.add("- Passo: " + entry.step);
    }
    if (entry.page != null) {
      lines.add("- Pagina: " + entry.page);
    }
    return String.join("\n", lines);
  }

  private String resolveOperationDetailHeading(OperationEnrichment enrichment, ItIndexEntry entry) {
    var normalizedWhat = this.intentDetector.normalize(firstNonBlank(enrichment.what(), entry.what));
    if (normalizedWhat.startsWith("monitorar")) {
      return "O que monitorar";
    }
    return "Como fazer";
  }

  private String extractOperationContext(ItIndexEntry entry, OperationEnrichment enrichment) {
    var rawHow = firstNonBlank(entry.how, "");
    if (!hasText(rawHow)) {
      return "";
    }

    var normalizedHow = AssistantTextSanitizer.sanitize(rawHow)
        .replace("\r", "\n")
        .trim();
    if (!hasText(normalizedHow)) {
      return "";
    }

    var bulletIndex = findFirstBulletIndex(normalizedHow);
    var leadingText = bulletIndex > 0
        ? normalizedHow.substring(0, bulletIndex).trim()
        : normalizedHow;

    leadingText = leadingText.replaceAll("\\s+", " ").trim();
    if (!hasText(leadingText)) {
      return "";
    }

    var normalizedLeading = this.intentDetector.normalize(leadingText);
    var normalizedWhat = this.intentDetector.normalize(firstNonBlank(enrichment.what(), entry.what));
    if (hasText(normalizedWhat) && normalizedLeading.equals(normalizedWhat)) {
      return "";
    }

    return toSentence(leadingText);
  }

  private int findFirstBulletIndex(String value) {
    if (!hasText(value)) {
      return -1;
    }

    var newlineBullet = value.indexOf("\n•");
    if (newlineBullet >= 0) {
      return newlineBullet;
    }

    newlineBullet = value.indexOf("\n-");
    if (newlineBullet >= 0) {
      return newlineBullet;
    }

    newlineBullet = value.indexOf("\n*");
    if (newlineBullet >= 0) {
      return newlineBullet;
    }

    var inlineBullet = value.indexOf("•");
    if (inlineBullet >= 0) {
      return inlineBullet;
    }

    return value.indexOf("- ");
  }

  private String buildFocusedSectionAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent,
      ItIndexEntry entry,
      ResponseSection requestedSection) {
    return switch (requestedSection) {
      case CARE -> buildFocusedCareAnswer(selectedIt, request, matches, index, intent, entry);
      case HOW -> buildFocusedHowAnswer(selectedIt, request, matches, index, intent, entry);
      case WHAT -> buildFocusedWhatAnswer(selectedIt, request, matches, index, intent, entry);
      case SOURCE -> buildFocusedSourceAnswer(selectedIt, request, matches, entry);
      case CAUSES -> buildFocusedCausesAnswer(entry);
      case ACTION -> buildFocusedActionAnswer(entry);
      case NONE -> switch (intent) {
        case OPERATION -> buildFastOperationAnswer(selectedIt, request, matches, index, entry);
        case ANOMALY -> buildFastAnomalyAnswer(entry);
        default -> buildFastGeneralAnswer(entry);
      };
    };
  }

  private String buildFastAnomalyAnswer(ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**Possiveis causas**");
    lines.add(formatBulletBlock(firstNonBlank(entry.possibleCauses, entry.what), "Nao identificado."));
    lines.add("");
    lines.add("**Acao**");
    lines.add(formatBulletBlock(firstNonBlank(entry.actionText, entry.how), "Nao identificado."));
    if (hasText(entry.care)) {
      lines.add("");
      lines.add("**Cuidados**");
      lines.add(formatBulletBlock(entry.care, "Nao identificado."));
    }
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildEntrySource(entry));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFocusedCareAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent,
      ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**Cuidados**");

    var care = intent == AssistantIntent.OPERATION
        ? firstNonBlank(enrichOperationEntry(entry, index).care(), entry.care)
        : firstNonBlank(entry.care);
    lines.add(formatBulletBlock(
        firstNonBlank(care),
        "Nao encontrei cuidados especiais claramente preenchidos nesse trecho da IT."));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildCompactSource(selectedIt, request, matches));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFocusedHowAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent,
      ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**Como fazer**");
    var how = intent == AssistantIntent.OPERATION
        ? firstNonBlank(enrichOperationEntry(entry, index).how(), entry.how, entry.actionText)
        : firstNonBlank(entry.how, entry.actionText);
    lines.add(formatBulletBlock(
        firstNonBlank(how),
        "Nao consegui localizar um passo a passo claro nesse trecho da IT."));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildCompactSource(selectedIt, request, matches));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFocusedWhatAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndex index,
      AssistantIntent intent,
      ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**O que fazer**");
    var what = intent == AssistantIntent.OPERATION
        ? firstNonBlank(enrichOperationEntry(entry, index).what(), entry.what)
        : firstNonBlank(entry.what, entry.sectionTitle);
    lines.add(firstNonBlank(what, "Nao identificado claramente nesse trecho da IT."));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildCompactSource(selectedIt, request, matches));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFocusedSourceAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches,
      ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildCompactSource(selectedIt, request, matches));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFocusedCausesAnswer(ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**Possiveis causas**");
    lines.add(formatBulletBlock(
        firstNonBlank(entry.possibleCauses, entry.what),
        "Nao encontrei as possiveis causas claramente preenchidas nesse trecho da IT."));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildEntrySource(entry));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFocusedActionAnswer(ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle)));
    lines.add("");
    lines.add("**Acao**");
    lines.add(formatBulletBlock(
        firstNonBlank(entry.actionText, entry.how),
        "Nao encontrei a acao claramente preenchida nesse trecho da IT."));
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildEntrySource(entry));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFastGeneralAnswer(ItIndexEntry entry) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.sectionTitle, entry.what)));
    lines.add("");
    lines.add("**Resposta**");
    lines.add(firstNonBlank(formatGeneralExcerpt(entry), "Nao consta trecho suficiente nesse bloco da IT."));
    if (hasText(entry.care)) {
      lines.add("");
      lines.add("**Cuidados**");
      lines.add(formatBulletBlock(entry.care, "Nao identificado."));
    }
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildEntrySource(entry));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private String buildFastSummaryLine(ItIndexEntry entry, String title) {
    var location = new ArrayList<String>();
    if (entry.step != null) {
      location.add("passo " + entry.step);
    }
    if (entry.page != null) {
      location.add("pagina " + entry.page);
    }

    var cleanTitle = firstNonBlank(title, "trecho relacionado");
    if (location.isEmpty()) {
      return "Encontrei este ponto na IT: " + cleanTitle + ".";
    }
    return "Encontrei este ponto na IT no " + String.join(", ", location) + ": " + cleanTitle + ".";
  }

  private String buildMinimalGroundedAnswer(
      ItEntity selectedIt,
      AssistantAskRequest request,
      ItIndexEntry entry,
      List<RankedEntry> matches) {
    var lines = new ArrayList<String>();
    lines.add(buildFastSummaryLine(entry, firstNonBlank(entry.what, entry.sectionTitle, "trecho relacionado")));
    lines.add("");
    lines.add("**Resposta**");
    lines.add(firstNonBlank(
        formatBulletBlock(firstNonBlank(entry.how, entry.actionText, entry.possibleCauses), ""),
        cleanGeneralParagraph(firstNonBlank(entry.how, entry.actionText, entry.possibleCauses)),
        "Encontrei o trecho na IT, mas ele veio incompleto na estruturacao atual."));
    if (hasText(entry.care)) {
      lines.add("");
      lines.add("**Cuidados**");
      lines.add(formatBulletBlock(entry.care, entry.care));
    }
    lines.add("");
    lines.add("**Fonte**");
    lines.add(buildCompactSource(selectedIt, request, matches));
    lines.add("");
    lines.add("Deseja saber algo mais?");
    return String.join("\n", lines);
  }

  private ResponseSection detectRequestedSection(String message) {
    var normalized = this.intentDetector.normalize(message);
    if (!hasText(normalized)) {
      return ResponseSection.NONE;
    }

    if (normalized.contains("cuidados especiais")
        || normalized.contains("cuidados")
        || normalized.contains("cuidado")) {
      return ResponseSection.CARE;
    }
    if (normalized.contains("como fazer")) {
      return ResponseSection.HOW;
    }
    if (normalized.contains("o que fazer")
        || normalized.equals("o que")
        || normalized.equals("e o que")) {
      return ResponseSection.WHAT;
    }
    if (normalized.contains("fonte")
        || normalized.contains("pagina")
        || normalized.contains("passo")) {
      return ResponseSection.SOURCE;
    }
    if (normalized.contains("possiveis causas")
        || normalized.contains("causas")) {
      return ResponseSection.CAUSES;
    }
    if (normalized.contains("acao")
        || normalized.contains("acoes")) {
      return ResponseSection.ACTION;
    }
    return ResponseSection.NONE;
  }

  private String buildCompactSource(
      ItEntity selectedIt,
      AssistantAskRequest request,
      List<RankedEntry> matches) {
    var entry = matches.get(0).entry();
    var lines = new ArrayList<String>();
    lines.add("Documento: " + resolveDocumentCode(selectedIt, request, matches));
    lines.add("Titulo: " + firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), entry.documentTitle, selectedIt.getDocumento()));
    if (hasReliableRevision(selectedIt.getRevisao())) {
      lines.add("Revisao: " + selectedIt.getRevisao());
    }
    if (selectedIt.getDataPublicacao() != null) {
      lines.add("Data de publicacao: " + selectedIt.getDataPublicacao().toLocalDate());
    }
    if (entry.page != null) {
      lines.add("Pagina: " + entry.page);
    }
    if (entry.step != null) {
      lines.add("Passo: " + entry.step);
    }
    return String.join("\n", lines);
  }

  private String buildEntrySource(ItIndexEntry entry) {
    var source = new ArrayList<String>();
    source.add("Documento: " + firstNonBlank(entry.documentCode, "-"));
    source.add("Pagina: " + (entry.page == null ? "-" : entry.page));
    if (entry.step != null) {
      source.add("Passo: " + entry.step);
    }
    return String.join("\n", source);
  }

  private boolean looksLikeDuplicatedHeading(String what, String sectionTitle) {
    var left = this.intentDetector.normalize(what);
    var right = this.intentDetector.normalize(sectionTitle);
    return hasText(left) && hasText(right) && (left.equals(right) || left.startsWith(right) || right.startsWith(left));
  }

  private String cleanGeneralLine(String value) {
    return firstNonBlank(value, "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String cleanGeneralParagraph(String value) {
    var text = AssistantTextSanitizer.sanitize(firstNonBlank(value, ""))
        .replace("•", "- ")
        .replace("", "- ")
        .replace("â€¢", "- ")
        .replaceAll("\\s*\\n\\s*", "\n")
        .trim();
    return formatBulletBlock(text, text);
  }

  private String buildDocumentHeader(ItEntity selectedIt, ItIndexEntry entry, List<RankedEntry> matches) {
    var pages = matches.stream()
        .map(RankedEntry::entry)
        .map(item -> item.page)
        .filter(page -> page != null)
        .distinct()
        .sorted()
        .map(String::valueOf)
        .collect(Collectors.joining(", "));
    var lines = new ArrayList<String>();
    lines.add("**Documento**");
    lines.add("Titulo: " + firstNonBlank(entry.documentTitle, selectedIt.getTitulo(), selectedIt.getDocumento()));
    lines.add("Documento: " + firstNonBlank(entry.documentCode, selectedIt.getDocumento()));
    lines.add("Revisao: " + firstNonBlank(selectedIt.getRevisao(), "-"));
    lines.add("Status: " + firstNonBlank(selectedIt.getStatus(), "-"));
    lines.add("Data de publicacao: " + (selectedIt.getDataPublicacao() == null ? "-" : selectedIt.getDataPublicacao().toLocalDate()));
    lines.add("Autor: " + firstNonBlank(entry.author, "-"));
    lines.add("Autorizador: " + firstNonBlank(entry.authorizer, "-"));
    lines.add("Data de impressao: " + firstNonBlank(entry.printDate, "-"));
    lines.add("Data de criacao: " + firstNonBlank(entry.createDate, "-"));
    lines.add("Pagina" + (pages.contains(",") ? "s" : "") + ": " + firstNonBlank(pages, "-"));
    return String.join("\n", lines);
  }

  private OperationEnrichment enrichOperationEntry(ItIndexEntry entry, ItIndex index) {
    var stepEntries = collectOperationStepEntries(entry, index);
    var primaryEntry = stepEntries.get(0);
    var repairedPrimary = repairOperationLabelAndHow(primaryEntry.what, primaryEntry.how);
    var what = cleanOperationWhat(repairedPrimary.what());
    var howParts = new ArrayList<String>();
    var careParts = new ArrayList<String>();

    for (int indexPosition = 0; indexPosition < stepEntries.size(); indexPosition += 1) {
      var candidate = stepEntries.get(indexPosition);
      var repairedCandidate = indexPosition == 0
          ? repairedPrimary
          : repairOperationLabelAndHow(candidate.what, candidate.how);
      var normalizedOperation = normalizeOperationText(repairedCandidate.what(), repairedCandidate.how(), candidate.care);
      if (hasText(normalizedOperation.how())) {
        howParts.add(normalizedOperation.how());
      }
      if (hasText(normalizedOperation.care())) {
        careParts.add(normalizedOperation.care());
      }

      var candidateWhat = firstNonBlank(candidate.what, "");
      var sensorMatch = Pattern.compile("^(ZS\\s*\\d+\\/\\d+\\s*e\\s*\\d+\\/\\d+)\\s*(.*)$", Pattern.CASE_INSENSITIVE)
          .matcher(candidateWhat.trim());
      if (sensorMatch.find()) {
        howParts.add("- " + sensorMatch.group(1).trim());
        var trailing = sensorMatch.group(2).trim();
        if (hasText(trailing) && hasCareCue(trailing)) {
          careParts.add(trailing);
        }
      } else if (hasCareCue(candidateWhat)) {
        careParts.add(candidateWhat.trim());
      }
    }

    if (careParts.isEmpty()) {
      collectNearbyCareFragments(entry, index).stream()
          .filter(this::hasText)
          .forEach(careParts::add);
    }

    return new OperationEnrichment(
        what,
        deduplicateLines(howParts),
        deduplicateLines(careParts));
  }

  private List<ItIndexEntry> collectOperationStepEntries(ItIndexEntry entry, ItIndex index) {
    var docCode = this.intentDetector.normalize(firstNonBlank(entry.documentCode, ""));
    var step = entry.step;
    var basePage = entry.page == null ? Integer.MAX_VALUE : entry.page;

    var sameStepEntries = index.entries.stream()
        .filter(candidate -> "step".equalsIgnoreCase(firstNonBlank(candidate.entryType, "")))
        .filter(candidate -> this.intentDetector.normalize(firstNonBlank(candidate.documentCode, "")).equals(docCode))
        .filter(candidate -> step != null && step.equals(candidate.step))
        .sorted(Comparator.comparing(candidate -> candidate.page == null ? Integer.MAX_VALUE : candidate.page))
        .toList();

    if (!sameStepEntries.isEmpty()) {
      var ordered = new ArrayList<ItIndexEntry>();
      ordered.add(entry);
      sameStepEntries.stream()
          .filter(candidate -> candidate != entry)
          .forEach(ordered::add);
      return ordered;
    }

    return List.of(entry).stream()
        .filter(candidate -> candidate.page == null || candidate.page >= basePage)
        .toList();
  }

  private List<String> collectNearbyCareFragments(ItIndexEntry entry, ItIndex index) {
    if (entry.page == null || entry.step == null) {
      return List.of();
    }

    return index.entries.stream()
        .filter(candidate -> candidate != entry)
        .filter(candidate -> candidate.page != null && candidate.page.equals(entry.page))
        .filter(candidate -> candidate.step != null && candidate.step.equals(entry.step))
        .filter(candidate -> firstNonBlank(candidate.entryType, "").equalsIgnoreCase("step"))
        .filter(candidate -> !hasText(candidate.how))
        .map(candidate -> firstNonBlank(candidate.care, candidate.what))
        .filter(this::hasText)
        .filter(this::hasCareCue)
        .limit(3)
        .toList();
  }

  private String deduplicateLines(List<String> parts) {
    var seen = new LinkedHashSet<String>();
    for (var part : parts) {
      for (var line : splitBulletAware(part)) {
        if (hasText(line)) {
          seen.add(line.trim());
        }
      }
    }
    return String.join("\n", seen);
  }

  private List<String> splitBulletAware(String value) {
    var text = firstNonBlank(value, "").trim();
    if (!hasText(text)) {
      return List.of();
    }
    return List.of(text
        .replace("\n- ", "|||")
        .replace(" - ", "|||")
        .split("\\|\\|\\|")).stream()
        .map(String::trim)
        .map(segment -> segment.replaceAll("^[-;:]+|[-;:]+$", "").trim())
        .filter(this::hasText)
        .toList();
  }

  private OperationText normalizeOperationText(String rawWhat, String rawHow, String rawCare) {
    var cleanedWhat = cleanOperationWhat(rawWhat);
    var repairedColumns = repairOperationColumns(rawHow, rawCare);
    var how = cleanList(appendWithSpace(extractOperationLead(rawWhat, cleanedWhat), repairedColumns.how()));
    var care = cleanCare(repairedColumns.care());
    var leadingHowContinuation = extractLeadingHowContinuation(care);
    if (hasText(leadingHowContinuation)) {
      how = appendWithSpace(how, leadingHowContinuation);
      care = care.substring(Math.min(leadingHowContinuation.length(), care.length())).trim();
    }
    var careParts = splitSegments(care);
    var howContinuation = new ArrayList<String>();
    var careContinuation = new ArrayList<String>();

    for (var part : careParts) {
      if (hasCareCue(part)) {
        careContinuation.add(part);
      } else {
        howContinuation.add(part);
      }
    }

    if (!howContinuation.isEmpty()) {
      how = repairBrokenHowText(appendWithSpace(how, String.join(" ", howContinuation)));
    } else {
      how = repairBrokenHowText(how);
    }

    return new OperationText(
        how,
        repairBrokenHowText(String.join(" ", careContinuation).trim()));
  }

  private OperationText repairOperationColumns(String rawHow, String rawCare) {
    var how = firstNonBlank(rawHow, "").trim();
    var care = firstNonBlank(rawCare, "").trim();

    var leadingContinuation = extractLeadingHowContinuation(care);
    if (hasText(leadingContinuation) && hasOpenStructure(how)) {
      how = appendWithSpace(how, leadingContinuation);
      care = care.substring(Math.min(leadingContinuation.length(), care.length())).trim();
    }

    var firstCareSegmentIndex = findFirstCareCueIndex(care);
    if (firstCareSegmentIndex > 0 && endsWithIncompleteStructure(how)) {
      var continuation = care.substring(0, firstCareSegmentIndex).trim();
      if (hasText(continuation)) {
        how = appendWithSpace(how, continuation);
        care = care.substring(firstCareSegmentIndex).trim();
      }
    }

    return new OperationText(how, care);
  }

  private String cleanOperationWhat(String what) {
    var value = firstNonBlank(what, "").trim();
    if (!hasText(value)) {
      return "";
    }

    for (var separator : OPERATION_SPLIT_MARKERS) {
      var index = value.indexOf(separator);
      if (index > 0) {
        return value.substring(0, index).trim();
      }
    }
    return value;
  }

  private OperationLabelRepair repairOperationLabelAndHow(String rawWhat, String rawHow) {
    var what = firstNonBlank(rawWhat, "").trim();
    var how = firstNonBlank(rawHow, "").trim();
    if (!hasText(what) || !hasText(how)) {
      return new OperationLabelRepair(what, how);
    }

    var trailingFragment = extractTrailingLabelContinuation(how);
    if (!hasText(trailingFragment) || what.endsWith(".") || what.endsWith(":")) {
      return new OperationLabelRepair(what, how);
    }

    var mergedWhat = mergeBrokenLabel(what, trailingFragment);
    if (!hasText(mergedWhat) || mergedWhat.equals(what)) {
      return new OperationLabelRepair(what, how);
    }

    var trimmedHow = how.substring(0, Math.max(0, how.length() - trailingFragment.length())).trim();
    return new OperationLabelRepair(mergedWhat, trimmedHow);
  }

  private String extractTrailingLabelContinuation(String how) {
    var text = firstNonBlank(how, "").trim();
    if (!hasText(text)) {
      return "";
    }

    var matcher = Pattern.compile("([a-z]{1,2}(?:\\s+de\\s+[a-z][a-z0-9/-]*){0,2})\\s*$").matcher(text);
    if (!matcher.find()) {
      return "";
    }

    var suffix = matcher.group(1).trim();
    var normalizedSuffix = this.intentDetector.normalize(suffix);
    if (normalizedSuffix.contains("kgf")
        || normalizedSuffix.contains("cm")
        || normalizedSuffix.matches(".*\\d.*")) {
      return "";
    }
    return suffix.split("\\s+").length <= 4 ? suffix : "";
  }

  private String mergeBrokenLabel(String what, String suffix) {
    var left = firstNonBlank(what, "").trim();
    var right = firstNonBlank(suffix, "").trim();
    if (!hasText(left) || !hasText(right)) {
      return left;
    }

    var parts = right.split("\\s+");
    if (parts.length == 0) {
      return left;
    }

    if (parts[0].length() <= 2) {
      left = left + parts[0];
      if (parts.length == 1) {
        return left;
      }
      return left + " " + String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }
    return left + " " + right;
  }

  private String extractOperationLead(String rawWhat, String cleanedWhat) {
    var value = firstNonBlank(rawWhat, "").trim();
    if (!hasText(value) || !hasText(cleanedWhat)) {
      return "";
    }

    for (var separator : OPERATION_SPLIT_MARKERS) {
      var index = value.indexOf(separator);
      if (index > 0) {
        return value.substring(index).trim();
      }
    }

    if (value.length() > cleanedWhat.length()) {
      return value.substring(cleanedWhat.length()).trim();
    }
    return "";
  }

  private String cleanList(String value) {
    return AssistantTextSanitizer.sanitize(firstNonBlank(value, ""))
        .replace(" - ", "\n- ")
        .replace("•", "\n- ")
        .replace("â€¢", "\n- ")
        .trim();
  }

  private String cleanCare(String value) {
    var cleaned = firstNonBlank(value, "").trim();
    if (!hasText(cleaned)) {
      return "";
    }
    if (this.intentDetector.normalize(cleaned).equals("filtro sensores do bipartido")) {
      return "";
    }
    return cleaned;
  }

  private boolean hasCareCue(String value) {
    var normalized = this.intentDetector.normalize(value);
    if (!hasText(normalized)) {
      return false;
    }
    return CARE_HINTS.stream().anyMatch(normalized::contains)
        || normalized.contains("segur")
        || normalized.startsWith("realizar o bloqueio")
        || normalized.startsWith("cuidar para");
  }

  private int findFirstCareCueIndex(String value) {
    var text = firstNonBlank(value, "");
    if (!hasText(text)) {
      return Integer.MAX_VALUE;
    }

    var normalized = this.intentDetector.normalize(text);
    var firstIndex = Integer.MAX_VALUE;
    for (var cue : CARE_HINTS) {
      var normalizedCue = this.intentDetector.normalize(cue);
      var normalizedIndex = normalized.indexOf(normalizedCue);
      if (normalizedIndex <= 0) {
        continue;
      }

      var prefix = normalized.substring(0, normalizedIndex);
      var originalIndex = Math.min(prefix.length(), text.length());
      if (originalIndex < firstIndex) {
        firstIndex = originalIndex;
      }
    }
    return firstIndex;
  }

  private String extractLeadingHowContinuation(String care) {
    var cleaned = firstNonBlank(care, "").trim();
    if (!hasText(cleaned)) {
      return "";
    }

    var firstCareIndex = findFirstCareCueIndex(cleaned);

    if (firstCareIndex == Integer.MAX_VALUE) {
      return "";
    }

    var prefix = cleaned.substring(0, firstCareIndex).trim();
    if (!hasText(prefix)) {
      return "";
    }

    var normalizedPrefix = this.intentDetector.normalize(prefix);
    if (normalizedPrefix.startsWith("limpando ")
        || normalizedPrefix.startsWith("utilizando ")
        || normalizedPrefix.startsWith("retirando ")
        || normalizedPrefix.startsWith("para ")
        || normalizedPrefix.startsWith("com ")
        || Character.isLowerCase(prefix.charAt(0))) {
      return prefix;
    }

    return "";
  }

  private boolean hasOpenStructure(String value) {
    var text = firstNonBlank(value, "");
    if (!hasText(text)) {
      return false;
    }
    var opens = text.chars().filter(character -> character == '(').count();
    var closes = text.chars().filter(character -> character == ')').count();
    return opens > closes || endsWithIncompleteStructure(text);
  }

  private boolean endsWithIncompleteStructure(String value) {
    var normalized = this.intentDetector.normalize(value);
    if (!hasText(normalized)) {
      return false;
    }
    return normalized.endsWith(" de")
        || normalized.endsWith(" do")
        || normalized.endsWith(" da")
        || normalized.endsWith(" para")
        || normalized.endsWith(" com")
        || normalized.endsWith(" e")
        || normalized.endsWith(" ou")
        || normalized.endsWith(" ao")
        || normalized.endsWith(" no")
        || normalized.endsWith(" na")
        || normalized.endsWith(" retirar o")
        || normalized.endsWith(" retirar a")
        || normalized.endsWith("(retirando o bico,");
  }

  private List<String> splitSegments(String value) {
    var cleaned = firstNonBlank(value, "")
        .replaceAll("(?<=\\))\\s+(?=\\p{Ll})", ". ")
        .trim();
    if (!hasText(cleaned)) {
      return List.of();
    }

    return List.of(cleaned.split("(?<=[.!?])\\s+|\\s+-\\s+")).stream()
        .map(String::trim)
        .map(segment -> segment.replaceAll("^[-.;:]+|[-.;:]+$", "").trim())
        .filter(this::hasText)
        .toList();
  }

  private String repairBrokenHowText(String value) {
    var text = firstNonBlank(value, "").trim();
    if (!hasText(text)) {
      return "";
    }

    text = text.replaceAll("\\s*\\n\\s*-\\s*", "\n- ");
    text = text.replace(" - Caso ", "\n- Caso ");
    text = text.replace(" - Parar ", "\n- Parar ");
    text = text.replace(" - Recolher ", "\n- Recolher ");
    text = text.replace(" - Realizar ", "\n- Realizar ");
    text = text.replace(" - Verificar ", "\n- Verificar ");
    text = text.replace(" - Inspecionar ", "\n- Inspecionar ");
    text = text.replace("  ", " ");
    text = text.replaceAll("\\s+\n", "\n").replaceAll("\n\\s+", "\n");
    return text.trim();
  }

  private String formatBulletBlock(String value, String fallback) {
    var text = firstNonBlank(value, "").trim();
    if (!hasText(text)) {
      return fallback;
    }

    var segments = List.of(text
        .replace("\n- ", "|||")
        .replace(" - ", "|||")
        .split("\\|\\|\\|")).stream()
        .map(String::trim)
        .map(segment -> segment.replaceAll("^[-;:]+|[-;:]+$", "").trim())
        .filter(this::hasText)
        .toList();

    if (segments.isEmpty()) {
      return fallback;
    }
    if (segments.size() == 1) {
      return segments.get(0);
    }

    return segments.stream()
        .map(segment -> "- " + segment)
        .collect(Collectors.joining("\n"));
  }

  private String resolveDocumentCode(ItEntity selectedIt, AssistantAskRequest request, List<RankedEntry> matches) {
    if (!matches.isEmpty() && hasText(matches.get(0).entry().documentCode)) {
      return matches.get(0).entry().documentCode.trim();
    }
    return firstNonBlank(selectedIt.getDocumento(), request.documentCode(), selectedIt.getTitulo());
  }

  private String appendWithSpace(String left, String right) {
    if (!hasText(left)) {
      return firstNonBlank(right, "");
    }
    if (!hasText(right)) {
      return left.trim();
    }
    return left.trim() + " " + right.trim();
  }

  private String buildDocumentInfoSource(ItEntity selectedIt, AssistantAskRequest request, Integer page) {
    var lines = new ArrayList<String>();
    lines.add("Documento: " + firstNonBlank(selectedIt.getDocumento(), request.documentCode(), "-"));
    lines.add("Titulo: " + firstNonBlank(selectedIt.getTitulo(), request.documentTitle(), selectedIt.getDocumento(), "-"));
    if (hasReliableRevision(selectedIt.getRevisao())) {
      lines.add("Revisao: " + selectedIt.getRevisao());
    }
    if (page != null) {
      lines.add("Pagina: " + page);
    }
    return String.join("\n", lines);
  }

  private String buildDocumentInfoIntro(ItEntity selectedIt, String title) {
    var safeTitle = firstNonBlank(title, "esse ponto");
    var documentTitle = firstNonBlank(selectedIt.getTitulo(), selectedIt.getDocumento(), "a IT selecionada");
    return switch (this.intentDetector.normalize(safeTitle)) {
      case "resultados esperados" ->
          "Na IT sobre " + documentTitle + ", encontrei os seguintes resultados esperados:";
      case "referencias" ->
          "Na IT sobre " + documentTitle + ", estas sao as referencias registradas:";
      case "anexos" ->
          "Na IT sobre " + documentTitle + ", estes sao os anexos informados:";
      case "definicoes" ->
          "Na IT sobre " + documentTitle + ", estas sao as definicoes registradas:";
      case "simbolos e abreviaturas", "abreviaturas", "simbolos" ->
          "Na IT sobre " + documentTitle + ", estes sao os simbolos e abreviaturas informados:";
      case "recursos necessarios" ->
          "Na IT sobre " + documentTitle + ", estes sao os recursos necessarios descritos:";
      default -> "";
    };
  }

  private String formatDocumentInfoBody(String title, String body) {
    var content = firstNonBlank(body, "Nao encontrei esse ponto de forma clara na IT selecionada.")
        .replace("\r", "\n")
        .replaceAll("\\s*\\n\\s*", "\n")
        .trim();

    if (!hasText(content)) {
      return "Nao encontrei esse ponto de forma clara na IT selecionada.";
    }

    var bullets = extractBulletLikeLines(content);
    if (!bullets.isEmpty()) {
      var lines = new ArrayList<String>();
      lines.add(bullets.stream()
          .map(item -> "- " + toSentence(item))
          .collect(Collectors.joining("\n")));

      if (shouldAddSectionFollowUp(title)) {
        lines.add("");
        lines.add(buildSectionFollowUp(title));
      }
      return String.join("\n", lines);
    }

    return toSentence(content);
  }

  private List<String> extractBulletLikeLines(String content) {
    var normalized = content
        .replace("•", "\n- ")
        .replace("", "\n- ")
        .replace("·", "\n- ")
        .replaceAll("(?<!\\n)-\\s+", "\n- ")
        .trim();

    var lines = normalized.lines()
        .map(String::trim)
        .filter(this::hasText)
        .map(line -> line.replaceAll("^[-•·]+\\s*", "").trim())
        .filter(this::hasText)
        .toList();

    if (lines.size() > 1) {
      return lines;
    }
    return List.of();
  }

  private boolean shouldAddSectionFollowUp(String title) {
    var normalizedTitle = this.intentDetector.normalize(title);
    return normalizedTitle.equals("resultados esperados")
        || normalizedTitle.equals("referencias")
        || normalizedTitle.equals("recursos necessarios");
  }

  private String buildSectionFollowUp(String title) {
    var normalizedTitle = this.intentDetector.normalize(title);
    return switch (normalizedTitle) {
      case "resultados esperados" ->
          "Para alcancar esses resultados, vale seguir os procedimentos descritos nos proximos passos da IT.";
      case "referencias" ->
          "Essas referencias complementam a execucao da atividade e ajudam a manter a operacao alinhada com os demais documentos.";
      case "recursos necessarios" ->
          "Esses recursos devem ser considerados junto com os procedimentos e cuidados descritos ao longo da IT.";
      default -> "";
    };
  }

  private boolean hasReliableRevision(String revisao) {
    var normalized = this.intentDetector.normalize(revisao);
    return hasText(normalized)
        && !normalized.equals("00")
        && !normalized.equals("0")
        && !normalized.equals("-");
  }

  private String toSentence(String value) {
    var text = firstNonBlank(value, "")
        .replaceAll("\\s+", " ")
        .replaceAll("^[-:;,.\\s]+", "")
        .trim();
    if (!hasText(text)) {
      return "";
    }
    return text.endsWith(".") || text.endsWith("!") || text.endsWith("?")
        ? text
        : text + ".";
  }

  private String decapitalize(String value) {
    var text = firstNonBlank(value, "");
    if (!hasText(text) || text.length() == 1) {
      return text.toLowerCase(Locale.ROOT);
    }
    return Character.toLowerCase(text.charAt(0)) + text.substring(1);
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return AssistantTextSanitizer.sanitize(value).trim();
      }
    }
    return "";
  }

  private record OperationText(String how, String care) {
  }

  private record OperationLabelRepair(String what, String how) {
  }

  private enum ResponseSection {
    NONE,
    WHAT,
    HOW,
    CARE,
    SOURCE,
    CAUSES,
    ACTION
  }
}
