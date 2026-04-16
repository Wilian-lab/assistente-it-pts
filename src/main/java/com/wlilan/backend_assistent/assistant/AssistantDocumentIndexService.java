package com.wlilan.backend_assistent.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wlilan.backend_assistent.assistant.model.ItIndex;
import com.wlilan.backend_assistent.assistant.model.ItIndexEntry;
import com.wlilan.backend_assistent.it.ItEntity;

@Service
public class AssistantDocumentIndexService {

  private static final Pattern LABEL_PATTERN = Pattern.compile("(?im)^\\s*(Titulo|Documento|Revisao|Status|Autor|Autorizador|Data Impressao|Data Criacao)\\s*:\\s*(.+)$");
  private static final Pattern STEP_PATTERN = Pattern.compile("(?m)^\\s*(\\d{1,3})\\s*$");
  private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d{1,3}");
  private static final Pattern CODE_LIKE_LABEL_PATTERN = Pattern.compile(
      "^(?:[a-z]{1,3}\\s*)?\\d[\\d./-]*(?:\\s+(?:e|item|itens?|zs|sensor|sensors?)\\s*[a-z0-9./-]+)*$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern FLATTENED_OPERATION_HEADER = Pattern.compile("(?is)PASSO\\s+O\\s+QUE\\s+FAZER\\s+COMO\\s+FAZER\\s+CUIDADOS\\s+ESPECIAIS");
  private static final Pattern FLATTENED_OPERATION_STEP = Pattern.compile("(?s)(\\d{1,3})\\s+(.+?)(?=(?:\\s\\d{1,3}\\s+\\p{Lu})|$)");
  private static final List<String> OPERATION_BODY_CUES = List.of(
      "Verificar ",
      "Ligar ",
      "Operacao com ",
      "Operacao com ",
      "Procedimento de ",
      "Monitorar ",
      "Ajustar ",
      "Inspecionar ",
      "Acionar ",
      "Reduzir ",
      "Fechar ",
      "Aguardar ",
      "Realizar ",
      "Confirmar ",
      "Manter ");
  private static final List<String> INLINE_CARE_CUES = List.of(
      "nunca ",
      "sempre ",
      "evitar ",
      "cuidar ",
      "risco ",
      "atencao ",
      "atencao ",
      "perigo ",
      "bloqueio ",
      "seguranca ",
      "seguranca ",
      "no inicio da operacao ",
      "no inicio da operacao ",
      "parada brusca ");
  private static final String INDEX_SCHEMA_VERSION = "assistant-index-v8-multipage-step-carry";

  private final AssistantDocumentBlockRepository assistantDocumentBlockRepository;
  private final ObjectMapper objectMapper;
  private final AssistantIntentDetector assistantIntentDetector;
  private final Path itIndexPath;

  public AssistantDocumentIndexService(
      AssistantDocumentBlockRepository assistantDocumentBlockRepository,
      ObjectMapper objectMapper,
      AssistantIntentDetector assistantIntentDetector,
      @Value("${assistant.it-index.path}") String itIndexPath) {
    this.assistantDocumentBlockRepository = assistantDocumentBlockRepository;
    this.objectMapper = objectMapper;
    this.assistantIntentDetector = assistantIntentDetector;
    this.itIndexPath = Path.of(itIndexPath);
  }

  @Transactional
  public ItIndex loadIndexFor(ItEntity it) {
    ensureIndexed(it);
    var entries = this.assistantDocumentBlockRepository.findByItIdOrderByPageAscStepAsc(it.getId()).stream()
        .map(this::toIndexEntry)
        .toList();
    var index = new ItIndex();
    index.entries = new ArrayList<>(entries);
    return index;
  }

  public DocumentMetadata extractDocumentMetadata(Path filePath, String fallbackDocumento, String fallbackTitulo) {
    if (filePath == null || !Files.exists(filePath)) {
      return new DocumentMetadata(
          firstNonBlank(fallbackDocumento),
          firstNonBlank(fallbackTitulo, fallbackDocumento),
          "",
          "",
          "",
          "",
          "");
    }

    var fallback = new ItEntity();
    fallback.setDocumento(firstNonBlank(fallbackDocumento));
    fallback.setTitulo(firstNonBlank(fallbackTitulo, fallbackDocumento));
    fallback.setRevisao("");
    fallback.setStatus("");

    try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
      var stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      var metadata = new ExtractedMetadata();
      var pageLimit = Math.min(document.getNumberOfPages(), 3);

      for (int pageIndex = 0; pageIndex < pageLimit; pageIndex += 1) {
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        var pageText = normalizePdfText(stripper.getText(document));
        metadata.absorb(pageText, fallback);
      }

      return new DocumentMetadata(
          firstNonBlank(metadata.documento, fallbackDocumento),
          firstNonBlank(metadata.titulo, fallbackTitulo, fallbackDocumento),
          normalizeRevisionValue(metadata.revisao),
          firstNonBlank(metadata.autor),
          firstNonBlank(metadata.autorizador),
          firstNonBlank(metadata.dataImpressao),
          firstNonBlank(metadata.dataCriacao));
    } catch (IOException exception) {
      return new DocumentMetadata(
          firstNonBlank(fallbackDocumento),
          firstNonBlank(fallbackTitulo, fallbackDocumento),
          "",
          "",
          "",
          "",
          "");
    }
  }

  public void ensureIndexed(ItEntity it) {
    if (it == null || it.getId() == null || !hasText(it.getFileUrl())) {
      return;
    }

    var sourceHash = resolveSourceHash(it);
    var current = this.assistantDocumentBlockRepository.findFirstByItIdOrderByUpdatedAtDesc(it.getId());
    if (current.isPresent() && sourceHash.equals(current.get().getSourceHash())) {
      return;
    }

    var entries = loadStructuredEntries(it).orElseGet(() -> extractPdfEntries(it)).stream()
        .map(entry -> sanitizeEntry(entry, it))
        .filter(this::isUsefulAssistantEntry)
        .toList();
    if (entries.isEmpty()) {
      return;
    }

    this.assistantDocumentBlockRepository.deleteByItId(it.getId());
    var now = LocalDateTime.now();
    var blocks = entries.stream()
        .map(entry -> toEntity(it, entry, sourceHash, now))
        .sorted(Comparator.comparing((AssistantDocumentBlockEntity entry) -> entry.getPage() == null ? Integer.MAX_VALUE : entry.getPage())
            .thenComparing(entry -> entry.getStep() == null ? Integer.MAX_VALUE : entry.getStep()))
        .toList();
    this.assistantDocumentBlockRepository.saveAll(blocks);
  }

  private Optional<List<ItIndexEntry>> loadStructuredEntries(ItEntity it) {
    if (!Files.exists(this.itIndexPath)) {
      return Optional.empty();
    }
    try {
      var index = this.objectMapper.readValue(this.itIndexPath.toFile(), ItIndex.class);
      var docCandidates = buildDocCandidates(it);
      var entries = index.entries == null ? List.<ItIndexEntry>of() : index.entries.stream()
          .filter(entry -> matchesDocument(entry, docCandidates))
          .toList();
      return entries.isEmpty() ? Optional.empty() : Optional.of(entries);
    } catch (IOException exception) {
      return Optional.empty();
    }
  }

  private List<ItIndexEntry> extractPdfEntries(ItEntity it) {
    var filePath = Path.of(it.getFileUrl());
    if (!Files.exists(filePath)) {
      return List.of();
    }

    try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
      var stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      var metadata = new ExtractedMetadata();
      var entries = new ArrayList<ItIndexEntry>();
      Integer carryStep = null;
      String carryWhat = "";
      TableKind carryTableKind = TableKind.NONE;

      for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex += 1) {
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        var pageText = normalizePdfText(stripper.getText(document));
        metadata.absorb(pageText, it);
        var tableResult = extractTableEntries(document, it, metadata, pageIndex + 1, carryStep, carryWhat, carryTableKind);
        var pageEntries = tableResult.entries().isEmpty()
            ? parseTextTableEntries(it, metadata, pageText, pageIndex + 1, carryStep)
            : tableResult.entries();
        if (pageEntries.isEmpty()) {
          pageEntries = parsePageEntries(it, metadata, pageText, pageIndex + 1, carryStep);
        }
        entries.addAll(pageEntries);

        if (!pageEntries.isEmpty()) {
          var lastStepEntry = pageEntries.stream()
              .filter(entry -> entry.step != null)
              .reduce((left, right) -> right)
              .orElse(null);
          if (lastStepEntry != null) {
            carryStep = lastStepEntry.step;
            carryWhat = firstNonBlank(lastStepEntry.what, carryWhat);
            carryTableKind = TableKind.fromEntryType(lastStepEntry.entryType);
          }
        }
      }

      return entries;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Nao foi possivel indexar o PDF da IT selecionada.");
    }
  }

  private String normalizeRevisionValue(String value) {
    var normalized = firstNonBlank(value).trim();
    if (!hasText(normalized)) {
      return "";
    }

    var matcher = Pattern.compile("(\\d{1,3})").matcher(normalized);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return normalized;
  }

  private List<ItIndexEntry> parsePageEntries(
      ItEntity it,
      ExtractedMetadata metadata,
      String pageText,
      int pageNumber,
      Integer carryStep) {
    var entries = new ArrayList<ItIndexEntry>();
    var sections = splitSections(pageText);
    Integer currentStep = extractStepHint(pageText);
    if (currentStep == null && shouldContinuePreviousStep(pageText, sections, carryStep)) {
      currentStep = carryStep;
    }

    for (var section : sections) {
      if (!hasText(section)) {
        continue;
      }
      var entry = buildSectionEntry(it, metadata, section, pageNumber);
      if (looksLikeAnomaly(section)) {
        entry.entryType = "anomaly";
        entries.add(entry);
        continue;
      }
      if (currentStep != null) {
        entry.entryType = "step";
        entry.step = currentStep;
        entries.add(entry);
      }
    }

    return entries;
  }

  private List<ItIndexEntry> parseTextTableEntries(
      ItEntity it,
      ExtractedMetadata metadata,
      String pageText,
      int pageNumber,
      Integer carryStep) {
    if (FLATTENED_OPERATION_HEADER.matcher(firstNonBlank(pageText, "")).find()) {
      return parseFlattenedOperationTable(it, metadata, pageText, pageNumber, carryStep);
    }
    return List.of();
  }

  private List<ItIndexEntry> parseFlattenedOperationTable(
      ItEntity it,
      ExtractedMetadata metadata,
      String pageText,
      int pageNumber,
      Integer carryStep) {
    var headerMatcher = FLATTENED_OPERATION_HEADER.matcher(firstNonBlank(pageText, ""));
    if (!headerMatcher.find()) {
      return List.of();
    }

    var flattened = cleanCell(pageText.substring(headerMatcher.end()));
    if (!hasText(flattened)) {
      return List.of();
    }

    var matcher = Pattern.compile("(?ms)(?:^|\\n)(\\d{1,3})\\s+(.+?)(?=(?:\\n\\d{1,3}\\s+)|$)").matcher(flattened);
    var entries = new ArrayList<ItIndexEntry>();

    while (matcher.find()) {
      var step = Integer.valueOf(matcher.group(1));
      var rawBlock = cleanCell(matcher.group(2));
      if (!hasText(rawBlock)) {
        continue;
      }

      var parsed = splitFlattenedOperationBlock(rawBlock);
      if (!hasText(parsed.what(), parsed.how(), parsed.care())) {
        continue;
      }
      var entry = buildBaseTableEntry(it, metadata, pageNumber, "step");
      entry.step = step;
      entry.what = parsed.what();
      entry.how = parsed.how();
      entry.care = parsed.care();
      entries.add(finalizeEntry(entry, it));
    }

    if (entries.isEmpty() && carryStep != null) {
      var continuation = splitFlattenedOperationBlock(flattened);
      if (hasText(continuation.what(), continuation.how(), continuation.care())) {
        var entry = buildBaseTableEntry(it, metadata, pageNumber, "step");
        entry.step = carryStep;
        entry.what = continuation.what();
        entry.how = continuation.how();
        entry.care = continuation.care();
        entries.add(finalizeEntry(entry, it));
      }
    }

    return entries;
  }

  private ParsedOperationBlock splitFlattenedOperationBlock(String block) {
    var cleaned = cleanCell(block);
    if (!hasText(cleaned)) {
      return new ParsedOperationBlock("", "", "");
    }

    var bodyStart = findOperationBodyStart(cleaned);
    var what = bodyStart > 0 ? cleanCell(cleaned.substring(0, bodyStart)) : cleaned;
    var how = bodyStart > 0 ? cleanCell(cleaned.substring(bodyStart)) : "";
    var care = extractInlineCare(how);
    return new ParsedOperationBlock(what, how, care);
  }

  private int findOperationBodyStart(String block) {
    int best = Integer.MAX_VALUE;
    for (var cue : OPERATION_BODY_CUES) {
      var matcher = Pattern.compile(Pattern.quote(cue), Pattern.CASE_INSENSITIVE).matcher(block);
      if (matcher.find() && matcher.start() > 3 && matcher.start() < best) {
        best = matcher.start();
      }
    }
    return best == Integer.MAX_VALUE ? -1 : best;
  }

  private String extractInlineCare(String value) {
    return splitInlineSegments(value).stream()
        .filter(this::hasInlineCareCue)
        .distinct()
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private List<String> splitInlineSegments(String value) {
    var prepared = cleanCell(value)
        .replace("•", "\n- ")
        .replaceAll("\\s+\\-\\s+", "\n- ")
        .trim();
    if (!hasText(prepared)) {
      return List.of();
    }

    return List.of(prepared.split("(?=\\n- )|(?<=[.!?])\\s+")).stream()
        .map(String::trim)
        .map(segment -> segment.replaceAll("^[-\\s]+", "").trim())
        .filter(this::hasText)
        .toList();
  }

  private boolean hasInlineCareCue(String value) {
    var normalized = this.assistantIntentDetector.normalize(value);
    if (!hasText(normalized)) {
      return false;
    }
    return INLINE_CARE_CUES.stream()
        .map(this.assistantIntentDetector::normalize)
        .anyMatch(normalized::contains);
  }

  private TableExtractionResult extractTableEntries(
      PDDocument document,
      ItEntity it,
      ExtractedMetadata metadata,
      int pageNumber,
      Integer carryStep,
      String carryWhat,
      TableKind carryTableKind) throws IOException {
    var chunks = extractPageChunks(document, pageNumber);
    if (chunks.isEmpty()) {
      return TableExtractionResult.empty();
    }

    var page = document.getPage(pageNumber - 1);
    var width = page.getMediaBox().getWidth();
    var height = page.getMediaBox().getHeight();
    var tableKind = detectTableKind(chunks);
    if (tableKind == TableKind.NONE) {
      return TableExtractionResult.empty();
    }

    var headerY = detectHeaderY(chunks, tableKind);
    var rows = buildRows(chunks, width, height, headerY, tableKind);
    if (rows.isEmpty()) {
      return TableExtractionResult.empty();
    }

    var entries = new ArrayList<ItIndexEntry>();
    ItIndexEntry current = null;
    Integer currentStep = carryStep;
    String currentWhat = firstNonBlank(carryWhat, "");

    for (var row : rows) {
      if (!row.hasContent()) {
        continue;
      }

      if (tableKind == TableKind.OPERATION) {
        var parsedStep = parseStep(row.step());
        if (parsedStep != null) {
          if (isUsefulTableEntry(current)) {
            entries.add(finalizeEntry(current, it));
          }
          current = buildBaseTableEntry(it, metadata, pageNumber, "step");
          current.step = parsedStep;
          currentStep = parsedStep;
          current.what = cleanCell(row.what());
          currentWhat = firstNonBlank(current.what, currentWhat);
          current.how = cleanCell(row.primary());
          current.care = cleanCell(row.care());
          continue;
        }

        if (current == null && currentStep != null && hasText(row.primary(), row.care(), row.what())) {
          current = buildBaseTableEntry(it, metadata, pageNumber, "step");
          current.step = currentStep;
          current.what = currentWhat;
        }

        if (current == null) {
          continue;
        }

        if (hasText(row.what())) {
          if (!hasText(current.what)) {
            current.what = cleanCell(row.what());
          } else if (shouldAppendToWhat(row.what(), row.primary(), row.care())) {
            current.what = appendCell(current.what, row.what());
          }
        }
        if (hasText(row.primary())) {
          current.how = appendCell(current.how, row.primary());
        }
        if (hasText(row.care())) {
          current.care = appendCell(current.care, row.care());
        }
      } else {
        var anomalyName = cleanCell(firstNonBlank(row.step(), row.what()));
        if (hasText(anomalyName) && !isLikelyContinuation(anomalyName)) {
          if (isUsefulTableEntry(current)) {
            entries.add(finalizeEntry(current, it));
          }
          current = buildBaseTableEntry(it, metadata, pageNumber, "anomaly");
          current.what = anomalyName;
          current.possibleCauses = cleanCell(row.what());
          current.actionText = cleanCell(row.primary());
          current.care = cleanCell(row.care());
          continue;
        }

        if (current == null) {
          continue;
        }
        if (hasText(row.what())) {
          current.possibleCauses = appendCell(current.possibleCauses, row.what());
        }
        if (hasText(row.primary())) {
          current.actionText = appendCell(current.actionText, row.primary());
        }
        if (hasText(row.care())) {
          current.care = appendCell(current.care, row.care());
        }
      }
    }

    if (isUsefulTableEntry(current)) {
      entries.add(finalizeEntry(current, it));
    }

    return entries.isEmpty()
        ? TableExtractionResult.empty()
        : new TableExtractionResult(entries, currentStep, currentWhat, tableKind);
  }

  private ItIndexEntry buildSectionEntry(ItEntity it, ExtractedMetadata metadata, String text, int pageNumber) {
    var lines = text.lines()
        .map(String::trim)
        .filter(this::hasText)
        .filter(line -> !isMetadataLine(line))
        .toList();

    var entry = new ItIndexEntry();
    entry.documentCode = firstNonBlank(metadata.documento, it.getDocumento());
    entry.documentTitle = firstNonBlank(metadata.titulo, it.getTitulo(), it.getDocumento());
    entry.author = firstNonBlank(metadata.autor, "");
    entry.authorizer = firstNonBlank(metadata.autorizador, "");
    entry.printDate = firstNonBlank(metadata.dataImpressao, "");
    entry.createDate = firstNonBlank(metadata.dataCriacao, "");
    entry.filePath = it.getFileUrl();
    entry.page = pageNumber;
    entry.entryType = "section";
    entry.sectionTitle = lines.isEmpty() ? "Conteudo da pagina" : crop(lines.get(0), 255);
    entry.what = lines.isEmpty() ? "" : lines.get(0);
    entry.how = lines.size() <= 1 ? "" : String.join("\n", lines.subList(1, lines.size()));
    entry.care = extractCare(lines);
    entry.normalizedWhat = this.assistantIntentDetector.normalize(entry.what);
    entry.normalizedHow = this.assistantIntentDetector.normalize(entry.how);
    entry.normalizedCare = this.assistantIntentDetector.normalize(entry.care);
    entry.normalized = this.assistantIntentDetector.normalize(String.join(" ",
        firstNonBlank(entry.what, ""),
        firstNonBlank(entry.how, ""),
        firstNonBlank(entry.care, "")));
    return entry;
  }

  private List<String> splitSections(String text) {
    var normalized = firstNonBlank(text, "").trim();
    if (!hasText(normalized)) {
      return List.of();
    }

    var rawSections = normalized.split("(?m)(?=^\\s*(?:\\d{1,3}\\s*$|\\p{Lu}[^\\n]{3,80}:?\\s*$))");
    return List.of(rawSections).stream()
        .map(String::trim)
        .filter(this::hasText)
        .limit(6)
        .toList();
  }

  private String extractCare(List<String> lines) {
    var careLines = lines.stream()
        .filter(line -> {
          var normalized = this.assistantIntentDetector.normalize(line);
          return normalized.contains("segur")
              || normalized.contains("bloqueio")
              || normalized.contains("loto")
              || normalized.startsWith("cuidar")
              || normalized.startsWith("evitar")
              || normalized.startsWith("sempre");
        })
        .toList();
    return String.join("\n", careLines);
  }

  private boolean shouldContinuePreviousStep(String pageText, List<String> sections, Integer carryStep) {
    if (carryStep == null || sections == null || sections.isEmpty()) {
      return false;
    }

    var normalizedPage = this.assistantIntentDetector.normalize(pageText);
    if (!hasText(normalizedPage)
        || normalizedPage.contains("anomalia")
        || normalizedPage.contains("acao corretiva")
        || normalizedPage.contains("possiveis causas")) {
      return false;
    }

    var meaningfulSections = sections.stream()
        .map(this::cleanCell)
        .filter(this::hasText)
        .filter(section -> !looksLikeCodeOrNoise(section))
        .toList();
    if (meaningfulSections.isEmpty()) {
      return false;
    }

    return meaningfulSections.stream()
        .map(this.assistantIntentDetector::normalize)
        .anyMatch(section -> section.contains("como fazer")
            || section.contains("o que fazer")
            || section.contains("cuidados especiais")
            || section.contains("anomali")
            || section.contains("verificar ")
            || section.contains("operar ")
            || section.contains("ligar ")
            || section.contains("monitorar ")
            || section.contains("ajustar ")
            || section.contains("procedimento ")
            || section.contains("manter ")
            || section.contains("reduzir ")
            || section.contains("fechar ")
            || section.contains("aguardar "));
  }

  private List<PositionedChunk> extractPageChunks(PDDocument document, int pageNumber) throws IOException {
    var stripper = new PositionedTextStripper();
    stripper.setSortByPosition(true);
    stripper.setStartPage(pageNumber);
    stripper.setEndPage(pageNumber);
    stripper.getText(document);
    return stripper.chunks();
  }

  private TableKind detectTableKind(List<PositionedChunk> chunks) {
    var normalizedPageText = this.assistantIntentDetector.normalize(
        chunks.stream().map(PositionedChunk::text).reduce("", (left, right) -> left + " " + right));

    if (normalizedPageText.contains("anomalia")
        && normalizedPageText.contains("possiveis causas")
        && normalizedPageText.contains("cuidados especiais")) {
      return TableKind.ANOMALY;
    }

    if (normalizedPageText.contains("passo")
        && normalizedPageText.contains("o que fazer")
        && normalizedPageText.contains("como fazer")
        && normalizedPageText.contains("cuidados especiais")) {
      return TableKind.OPERATION;
    }

    return TableKind.NONE;
  }

  private float detectHeaderY(List<PositionedChunk> chunks, TableKind tableKind) {
    var headerChunks = chunks.stream()
        .filter(chunk -> {
          var normalized = this.assistantIntentDetector.normalize(chunk.text());
          return switch (tableKind) {
            case OPERATION -> normalized.contains("passo")
                || normalized.contains("o que fazer")
                || normalized.contains("como fazer")
                || normalized.contains("cuidados especiais");
            case ANOMALY -> normalized.contains("anomalia")
                || normalized.contains("acao")
                || normalized.contains("cuidados especiais");
            case NONE -> false;
          };
        })
        .toList();

    if (headerChunks.isEmpty()) {
      return 90f;
    }

    return headerChunks.stream()
        .map(PositionedChunk::y)
        .min(Float::compare)
        .orElse(90f);
  }

  private List<TableRow> buildRows(List<PositionedChunk> chunks, float width, float height, float headerY, TableKind tableKind) {
    var tableChunks = chunks.stream()
        .filter(chunk -> chunk.y() > headerY + 10f)
        .filter(chunk -> chunk.y() < height - 35f)
        .filter(chunk -> !isFooterOrMetadata(chunk.text()))
        .sorted(Comparator.comparing(PositionedChunk::y).thenComparing(PositionedChunk::x))
        .toList();

    if (tableChunks.isEmpty()) {
      return List.of();
    }

    var lineGroups = new ArrayList<List<PositionedChunk>>();
    for (var chunk : tableChunks) {
      if (lineGroups.isEmpty()) {
        lineGroups.add(new ArrayList<>(List.of(chunk)));
        continue;
      }

      var currentGroup = lineGroups.get(lineGroups.size() - 1);
      var baselineY = currentGroup.get(0).y();
      if (Math.abs(chunk.y() - baselineY) <= 6f) {
        currentGroup.add(chunk);
      } else {
        lineGroups.add(new ArrayList<>(List.of(chunk)));
      }
    }

    var rows = new ArrayList<TableRow>();
    for (var lineGroup : lineGroups) {
      var step = new ArrayList<String>();
      var what = new ArrayList<String>();
      var primary = new ArrayList<String>();
      var care = new ArrayList<String>();

      for (var chunk : lineGroup) {
        var column = resolveColumn(chunk, width, tableKind);
        switch (column) {
          case STEP -> step.add(chunk.text());
          case WHAT -> what.add(chunk.text());
          case PRIMARY -> primary.add(chunk.text());
          case CARE -> care.add(chunk.text());
        }
      }

      var row = new TableRow(
          joinColumn(step),
          joinColumn(what),
          joinColumn(primary),
          joinColumn(care));
      if (row.hasContent()) {
        rows.add(row);
      }
    }

    return rows;
  }

  private TableColumn resolveColumn(PositionedChunk chunk, float width, TableKind tableKind) {
    var x = chunk.x();
    var stepEnd = width * 0.09f;
    var whatEnd = width * (tableKind == TableKind.ANOMALY ? 0.33f : 0.22f);
    var primaryEnd = width * (tableKind == TableKind.ANOMALY ? 0.62f : 0.77f);

    if (x < stepEnd) {
      return TableColumn.STEP;
    }
    if (x < whatEnd) {
      return TableColumn.WHAT;
    }
    if (x < primaryEnd) {
      return TableColumn.PRIMARY;
    }
    return TableColumn.CARE;
  }

  private String joinColumn(List<String> values) {
    return values.stream()
        .map(this::cleanCell)
        .filter(this::hasText)
        .reduce((left, right) -> left + " " + right)
        .orElse("");
  }

  private ItIndexEntry buildBaseTableEntry(ItEntity it, ExtractedMetadata metadata, int pageNumber, String entryType) {
    var entry = new ItIndexEntry();
    entry.documentCode = firstNonBlank(metadata.documento, it.getDocumento());
    entry.documentTitle = firstNonBlank(metadata.titulo, it.getTitulo(), it.getDocumento());
    entry.author = firstNonBlank(metadata.autor, "");
    entry.authorizer = firstNonBlank(metadata.autorizador, "");
    entry.printDate = firstNonBlank(metadata.dataImpressao, "");
    entry.createDate = firstNonBlank(metadata.dataCriacao, "");
    entry.page = pageNumber;
    entry.entryType = entryType;
    return entry;
  }

  private ItIndexEntry finalizeEntry(ItIndexEntry entry, ItEntity it) {
    entry.what = cleanCell(entry.what);
    entry.how = cleanCell(entry.how);
    entry.care = cleanCell(entry.care);
    entry.possibleCauses = cleanCell(entry.possibleCauses);
    entry.actionText = cleanCell(entry.actionText);
    entry.sectionTitle = crop(firstNonBlank(entry.what, entry.documentTitle, "Bloco estruturado"), 255);
    entry.normalizedWhat = this.assistantIntentDetector.normalize(entry.what);
    entry.normalizedHow = this.assistantIntentDetector.normalize(firstNonBlank(entry.how, entry.actionText));
    entry.normalizedCare = this.assistantIntentDetector.normalize(entry.care);
    entry.normalized = this.assistantIntentDetector.normalize(String.join(" ",
        firstNonBlank(entry.documentTitle, it.getTitulo(), it.getDocumento()),
        firstNonBlank(entry.what, ""),
        firstNonBlank(entry.how, ""),
        firstNonBlank(entry.care, ""),
        firstNonBlank(entry.possibleCauses, ""),
        firstNonBlank(entry.actionText, "")));
    return entry;
  }

  private ItIndexEntry sanitizeEntry(ItIndexEntry entry, ItEntity it) {
    if (entry == null) {
      return null;
    }

    entry.documentCode = firstNonBlank(entry.documentCode, it.getDocumento());
    entry.documentTitle = firstNonBlank(entry.documentTitle, it.getTitulo(), it.getDocumento());
    entry.entryType = this.assistantIntentDetector.normalize(firstNonBlank(entry.entryType, ""));

    entry.what = cleanCell(entry.what);
    entry.how = cleanCell(entry.how);
    entry.care = cleanCell(entry.care);
    entry.possibleCauses = cleanCell(entry.possibleCauses);
    entry.actionText = cleanCell(entry.actionText);

    if (!"step".equals(entry.entryType) && !"anomaly".equals(entry.entryType) && !"section".equals(entry.entryType)) {
      return null;
    }

    if (looksLikeCodeOrNoise(entry.what)) {
      entry.what = "";
    }
    if (looksLikeCodeOrNoise(entry.sectionTitle)) {
      entry.sectionTitle = "";
    }
    if (looksLikeCodeOrNoise(entry.possibleCauses) && !hasText(entry.actionText) && !hasText(entry.care)) {
      entry.possibleCauses = "";
    }

    if ("step".equals(entry.entryType) && entry.step == null) {
      return null;
    }

    entry.sectionTitle = crop(firstNonBlank(entry.what, entry.sectionTitle, entry.documentTitle, ""), 255);
    entry.normalizedWhat = this.assistantIntentDetector.normalize(entry.what);
    entry.normalizedHow = this.assistantIntentDetector.normalize(firstNonBlank(entry.how, entry.actionText));
    entry.normalizedCare = this.assistantIntentDetector.normalize(entry.care);
    entry.normalized = this.assistantIntentDetector.normalize(joinText(entry));
    return entry;
  }

  private boolean isUsefulAssistantEntry(ItIndexEntry entry) {
    if (entry == null) {
      return false;
    }
    if ("step".equals(entry.entryType)) {
      return entry.step != null
          && (hasMeaningfulLabel(entry.what)
              || hasText(entry.how)
              || hasText(entry.care));
    }
    if ("anomaly".equals(entry.entryType)) {
      return hasMeaningfulLabel(entry.what)
          || hasText(entry.possibleCauses)
          || hasText(entry.actionText)
          || hasText(entry.care);
    }
    if ("section".equals(entry.entryType)) {
      return hasMeaningfulLabel(entry.what)
          || hasText(entry.how)
          || hasText(entry.care);
    }
    return false;
  }

  private boolean hasMeaningfulLabel(String value) {
    var normalized = this.assistantIntentDetector.normalize(firstNonBlank(value, ""));
    if (!hasText(normalized) || looksLikeCodeOrNoise(normalized)) {
      return false;
    }
    return List.of(normalized.split("\\s+")).stream()
        .anyMatch(token -> token.length() >= 4 && token.chars().anyMatch(Character::isLetter));
  }

  private boolean looksLikeCodeOrNoise(String value) {
    var normalized = this.assistantIntentDetector.normalize(firstNonBlank(value, ""));
    if (!hasText(normalized)) {
      return true;
    }
    if (CODE_LIKE_LABEL_PATTERN.matcher(normalized).matches()) {
      return true;
    }
    if (normalized.startsWith("zs ")
        || normalized.startsWith("e ")
        || normalized.startsWith("item ")
        || normalized.startsWith("pagina ")
        || normalized.equals("conteudo da pagina")) {
      return true;
    }
    var tokens = List.of(normalized.split("\\s+")).stream()
        .filter(this::hasText)
        .toList();
    var digitHeavyTokens = tokens.stream()
        .filter(token -> token.chars().anyMatch(Character::isDigit))
        .count();
    return !tokens.isEmpty() && digitHeavyTokens >= Math.max(1, tokens.size() - 1);
  }

  private boolean isUsefulTableEntry(ItIndexEntry entry) {
    if (entry == null) {
      return false;
    }
    return hasText(entry.what)
        || hasText(entry.how)
        || hasText(entry.care)
        || hasText(entry.possibleCauses)
        || hasText(entry.actionText);
  }

  private Integer parseStep(String raw) {
    var matcher = DIGIT_PATTERN.matcher(firstNonBlank(raw, ""));
    return matcher.find() ? Integer.valueOf(matcher.group()) : null;
  }

  private boolean shouldAppendToWhat(String what, String primary, String care) {
    return hasText(what) && !hasText(primary) && !hasText(care);
  }

  private boolean isLikelyContinuation(String value) {
    var normalized = this.assistantIntentDetector.normalize(value);
    return normalized.startsWith("o ")
        || normalized.startsWith("de ")
        || normalized.startsWith("da ")
        || normalized.startsWith("do ")
        || normalized.startsWith("para ")
        || normalized.startsWith("com ");
  }

  private boolean isFooterOrMetadata(String value) {
    var normalized = this.assistantIntentDetector.normalize(value);
    return !hasText(normalized)
        || normalized.contains("copia nao controlada")
        || normalized.startsWith("autor")
        || normalized.startsWith("autorizador")
        || normalized.startsWith("data impressao")
        || normalized.startsWith("data criacao")
        || normalized.equals("passo")
        || normalized.equals("o que fazer")
        || normalized.equals("como fazer")
        || normalized.equals("cuidados especiais")
        || normalized.equals("anomalia")
        || normalized.equals("possiveis causas")
        || normalized.equals("acao");
  }

  private String cleanCell(String value) {
    return AssistantTextSanitizer.sanitize(firstNonBlank(value, ""))
        .replace('\u0000', ' ')
        .replace("•", "\n- ")
        .replace("", "\n- ")
        .replace("·", "\n- ")
        .replace("â€¢", "\n- ")
        .replaceAll("\\s*\\n\\s*", "\n")
        .replaceAll(" +", " ")
        .replaceAll("\n{3,}", "\n\n")
        .trim();
  }

  private String appendCell(String current, String addition) {
    var left = cleanCell(current);
    var right = cleanCell(addition);
    if (!hasText(left)) {
      return right;
    }
    if (!hasText(right)) {
      return left;
    }
    return (left + "\n" + right).trim();
  }

  private boolean looksLikeAnomaly(String section) {
    var normalized = this.assistantIntentDetector.normalize(section);
    return normalized.contains("possiveis causas")
        || normalized.contains("anomalia")
        || normalized.contains("acao corretiva");
  }

  private Integer extractStepHint(String text) {
    var matcher = STEP_PATTERN.matcher(firstNonBlank(text, ""));
    return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }

  private String normalizePdfText(String raw) {
    return AssistantTextSanitizer.sanitize(firstNonBlank(raw, ""))
        .replace('\u0000', ' ')
        .replace('\r', '\n')
        .replaceAll("[\\t\\x0B\\f]+", " ")
        .replaceAll("\\u00A0", " ")
        .replaceAll(" +", " ")
        .replaceAll("\n{3,}", "\n\n")
        .trim();
  }

  private boolean isMetadataLine(String line) {
    return LABEL_PATTERN.matcher(firstNonBlank(line, "")).find();
  }

  private AssistantDocumentBlockEntity toEntity(ItEntity it, ItIndexEntry entry, String sourceHash, LocalDateTime now) {
    var entity = new AssistantDocumentBlockEntity();
    entity.setItId(it.getId());
    entity.setDocumento(firstNonBlank(entry.documentCode, it.getDocumento()));
    entity.setTitulo(firstNonBlank(entry.documentTitle, it.getTitulo(), it.getDocumento()));
    entity.setRevisao(firstNonBlank(it.getRevisao(), ""));
    entity.setStatus(firstNonBlank(it.getStatus(), ""));
    entity.setAuthor(firstNonBlank(entry.author, ""));
    entity.setAuthorizer(firstNonBlank(entry.authorizer, ""));
    entity.setPrintDate(firstNonBlank(entry.printDate, ""));
    entity.setCreateDate(firstNonBlank(entry.createDate, ""));
    entity.setPage(entry.page);
    entity.setStep(entry.step);
    entity.setSectionNumber(entry.sectionNumber);
    entity.setSectionTitle(crop(entry.sectionTitle, 255));
    entity.setEntryType(firstNonBlank(entry.entryType, "section"));
    entity.setWhat(entry.what);
    entity.setHow(entry.how);
    entity.setCare(entry.care);
    entity.setPossibleCauses(entry.possibleCauses);
    entity.setActionText(entry.actionText);
    entity.setNormalized(firstNonBlank(entry.normalized, this.assistantIntentDetector.normalize(joinText(entry))));
    entity.setNormalizedWhat(firstNonBlank(entry.normalizedWhat, this.assistantIntentDetector.normalize(entry.what)));
    entity.setNormalizedHow(firstNonBlank(entry.normalizedHow, this.assistantIntentDetector.normalize(entry.how)));
    entity.setNormalizedCare(firstNonBlank(entry.normalizedCare, this.assistantIntentDetector.normalize(entry.care)));
    entity.setSourceHash(sourceHash);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  private ItIndexEntry toIndexEntry(AssistantDocumentBlockEntity entity) {
    var entry = new ItIndexEntry();
    entry.documentCode = entity.getDocumento();
    entry.documentTitle = entity.getTitulo();
    entry.author = entity.getAuthor();
    entry.authorizer = entity.getAuthorizer();
    entry.printDate = entity.getPrintDate();
    entry.createDate = entity.getCreateDate();
    entry.filePath = "";
    entry.page = entity.getPage();
    entry.step = entity.getStep();
    entry.sectionNumber = entity.getSectionNumber();
    entry.sectionTitle = entity.getSectionTitle();
    entry.entryType = entity.getEntryType();
    entry.what = entity.getWhat();
    entry.how = entity.getHow();
    entry.care = entity.getCare();
    entry.possibleCauses = entity.getPossibleCauses();
    entry.actionText = entity.getActionText();
    entry.normalized = entity.getNormalized();
    entry.normalizedWhat = entity.getNormalizedWhat();
    entry.normalizedHow = entity.getNormalizedHow();
    entry.normalizedCare = entity.getNormalizedCare();
    return entry;
  }

  private String resolveSourceHash(ItEntity it) {
    try {
      var path = Path.of(it.getFileUrl());
      var size = Files.exists(path) ? Files.size(path) : -1;
      var modified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1;
      return INDEX_SCHEMA_VERSION + "::" + path + "::" + size + "::" + modified;
    } catch (IOException exception) {
      return INDEX_SCHEMA_VERSION + "::" + firstNonBlank(it.getFileUrl(), "") + "::unknown";
    }
  }

  private List<String> buildDocCandidates(ItEntity it) {
    var values = new LinkedHashSet<String>();
    values.add(this.assistantIntentDetector.normalize(firstNonBlank(it.getDocumento(), "")));
    values.add(this.assistantIntentDetector.normalize(firstNonBlank(it.getTitulo(), "")));
    values.add(this.assistantIntentDetector.normalize(firstNonBlank(fileName(it.getFileUrl()), "")));
    values.add(this.assistantIntentDetector.normalize(firstNonBlank(it.getDocumento(), "").replaceAll("[^a-zA-Z0-9]", "")));
    values.add(this.assistantIntentDetector.normalize(firstNonBlank(it.getTitulo(), "").replaceAll("[^a-zA-Z0-9]", "")));
    return values.stream().filter(this::hasText).toList();
  }

  private boolean matchesDocument(ItIndexEntry entry, List<String> docCandidates) {
    var fields = List.of(
        this.assistantIntentDetector.normalize(firstNonBlank(entry.documentCode, "")),
        this.assistantIntentDetector.normalize(firstNonBlank(entry.documentTitle, "")),
        this.assistantIntentDetector.normalize(firstNonBlank(fileName(entry.filePath), "")),
        this.assistantIntentDetector.normalize(firstNonBlank(entry.documentCode, "").replaceAll("[^a-zA-Z0-9]", "")),
        this.assistantIntentDetector.normalize(firstNonBlank(entry.documentTitle, "").replaceAll("[^a-zA-Z0-9]", "")));
    return docCandidates.stream().anyMatch(candidate -> fields.stream().anyMatch(field -> hasText(field) && (field.equals(candidate) || field.contains(candidate))));
  }

  private String fileName(String value) {
    var normalized = firstNonBlank(value, "").replace("\\", "/");
    var slashIndex = normalized.lastIndexOf('/');
    return slashIndex < 0 ? normalized : normalized.substring(slashIndex + 1);
  }

  private String joinText(ItIndexEntry entry) {
    return String.join(" ",
        firstNonBlank(entry.documentTitle, ""),
        firstNonBlank(entry.sectionTitle, ""),
        firstNonBlank(entry.what, ""),
        firstNonBlank(entry.how, ""),
        firstNonBlank(entry.care, ""),
        firstNonBlank(entry.possibleCauses, ""),
        firstNonBlank(entry.actionText, ""));
  }

  private String crop(String value, int max) {
    var text = firstNonBlank(value, "");
    if (text.length() <= max) {
      return text;
    }
    return text.substring(0, max);
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private boolean hasText(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return true;
      }
    }
    return false;
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private static final class ExtractedMetadata {
    private String titulo;
    private String documento;
    private String revisao;
    private String status;
    private String autor;
    private String autorizador;
    private String dataImpressao;
    private String dataCriacao;

    private void absorb(String text, ItEntity it) {
      var matcher = LABEL_PATTERN.matcher(text);
      while (matcher.find()) {
        var label = matcher.group(1).trim().toLowerCase(Locale.ROOT);
        var value = matcher.group(2).trim();
        switch (label) {
          case "titulo" -> this.titulo = firstFilled(this.titulo, value);
          case "documento" -> this.documento = firstFilled(this.documento, value);
          case "revisao" -> this.revisao = firstFilled(this.revisao, value);
          case "status" -> this.status = firstFilled(this.status, value);
          case "autor" -> this.autor = firstFilled(this.autor, value);
          case "autorizador" -> this.autorizador = firstFilled(this.autorizador, value);
          case "data impressao" -> this.dataImpressao = firstFilled(this.dataImpressao, value);
          case "data criacao" -> this.dataCriacao = firstFilled(this.dataCriacao, value);
          default -> {
          }
        }
      }

      this.titulo = firstFilled(this.titulo, it.getTitulo());
      this.documento = firstFilled(this.documento, it.getDocumento());
      this.revisao = firstFilled(this.revisao, it.getRevisao());
      this.status = firstFilled(this.status, it.getStatus());
    }

    private static String firstFilled(String current, String next) {
      return current == null || current.isBlank() ? next : current;
    }
  }

  public record DocumentMetadata(
      String documento,
      String titulo,
      String revisao,
      String autor,
      String autorizador,
      String dataImpressao,
      String dataCriacao) {
  }

  private enum TableKind {
    NONE,
    OPERATION,
    ANOMALY;

    private static TableKind fromEntryType(String entryType) {
      if ("anomaly".equalsIgnoreCase(entryType)) {
        return ANOMALY;
      }
      if ("step".equalsIgnoreCase(entryType)) {
        return OPERATION;
      }
      return NONE;
    }
  }

  private enum TableColumn {
    STEP,
    WHAT,
    PRIMARY,
    CARE
  }

  private record PositionedChunk(String text, float x, float y) {
  }

  private record TableRow(String step, String what, String primary, String care) {
    private boolean hasContent() {
      return hasTextValue(step) || hasTextValue(what) || hasTextValue(primary) || hasTextValue(care);
    }

    private static boolean hasTextValue(String value) {
      return value != null && !value.trim().isBlank();
    }
  }

  private record TableExtractionResult(List<ItIndexEntry> entries, Integer carryStep, String carryWhat, TableKind carryTableKind) {
    private static TableExtractionResult empty() {
      return new TableExtractionResult(List.of(), null, "", TableKind.NONE);
    }
  }

  private record ParsedOperationBlock(String what, String how, String care) {
  }

  private static final class PositionedTextStripper extends PDFTextStripper {
    private final List<PositionedChunk> chunks = new ArrayList<>();

    private PositionedTextStripper() throws IOException {
      super();
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
      if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
        return;
      }

      var first = textPositions.get(0);
      this.chunks.add(new PositionedChunk(
          text.trim(),
          first.getXDirAdj(),
          first.getYDirAdj()));
    }

    private List<PositionedChunk> chunks() {
      return List.copyOf(this.chunks);
    }
  }
}
