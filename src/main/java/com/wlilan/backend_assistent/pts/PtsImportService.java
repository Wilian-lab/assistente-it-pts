package com.wlilan.backend_assistent.pts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.Security.SetorSupport;

import jakarta.transaction.Transactional;

@Service
public class PtsImportService {
  private static final Logger log = LoggerFactory.getLogger(PtsImportService.class);

  private static final int HEADER_ROW_INDEX = 6;
  private static final List<String> REQUIRED_COLUMNS = List.of("etapa", "item", "variavel");
  private static final List<String> DEFAULT_COLUMNS = List.of(
      "etapa",
      "item",
      "variavel",
      "classificacao",
      "unidade",
      "limite_inf",
      "limite_sup",
      "resp_coleta",
      "resp_analise",
      "frequencia",
      "ponto_coleta",
      "amostra",
      "metodo_analise",
      "tag",
      "tag_aspen",
      "acao_abaixo",
      "acao_acima",
      "fca",
      "vai_no_app",
      "documento_referencia");

  private static final Map<String, String> SHEET_MAP = Map.of(
      "1. Farelo", "Farelo",
      "2. Germe", "Germe",
      "3. Gluten", "Gluten");

  private static final Map<String, String> COLUMN_RENAME = new LinkedHashMap<>();

  static {
    COLUMN_RENAME.put("unnamed: 1", "etapa");
    COLUMN_RENAME.put("etapa", "etapa");
    COLUMN_RENAME.put("etapa do processo", "etapa");
    COLUMN_RENAME.put("etapa de processo", "etapa");
    COLUMN_RENAME.put("processo", "etapa");
    COLUMN_RENAME.put("item", "item");
    COLUMN_RENAME.put("variavel", "variavel");
    COLUMN_RENAME.put("classificacao", "classificacao");
    COLUMN_RENAME.put("unidade", "unidade");
    COLUMN_RENAME.put("limite inferior", "limite_inf");
    COLUMN_RENAME.put("limite superior", "limite_sup");
    COLUMN_RENAME.put("responsavel coleta", "resp_coleta");
    COLUMN_RENAME.put("responsavel analise", "resp_analise");
    COLUMN_RENAME.put("frequencia", "frequencia");
    COLUMN_RENAME.put("ponto de coleta", "ponto_coleta");
    COLUMN_RENAME.put("amostra", "amostra");
    COLUMN_RENAME.put("metodo analise", "metodo_analise");
    COLUMN_RENAME.put("metodo de analise", "metodo_analise");
    COLUMN_RENAME.put("tag", "tag");
    COLUMN_RENAME.put("tag aspen", "tag_aspen");
    COLUMN_RENAME.put("abaixo do limite", "acao_abaixo");
    COLUMN_RENAME.put("acao abaixo", "acao_abaixo");
    COLUMN_RENAME.put("acima do limite", "acao_acima");
    COLUMN_RENAME.put("acao acima", "acao_acima");
    COLUMN_RENAME.put("fca", "fca");
    COLUMN_RENAME.put("vai no app", "vai_no_app");
    COLUMN_RENAME.put("vai no apps", "vai_no_app");
    COLUMN_RENAME.put("documento referencia", "documento_referencia");
    COLUMN_RENAME.put("documento de referencia", "documento_referencia");
    COLUMN_RENAME.put("documentos de referencia", "documento_referencia");
    COLUMN_RENAME.put("doc referencia", "documento_referencia");
    COLUMN_RENAME.put("unnamed: 20", "documento_referencia");
  }

  private final PtsFileRepository ptsFileRepository;
  private final PtsRecordRepository ptsRecordRepository;
  private final DataFormatter dataFormatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
  private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

  public PtsImportService(PtsFileRepository ptsFileRepository, PtsRecordRepository ptsRecordRepository) {
    this.ptsFileRepository = ptsFileRepository;
    this.ptsRecordRepository = ptsRecordRepository;
  }

  @Transactional
  public void importFile(Path filePath, String setor) {
    var normalizedSetor = SetorSupport.normalize(setor);
    var records = readRecords(filePath, normalizedSetor);

    this.ptsRecordRepository.deleteBySetor(normalizedSetor);
    this.ptsRecordRepository.flush();
    this.ptsFileRepository.deleteBySetor(normalizedSetor);
    this.ptsFileRepository.flush();
    this.ptsRecordRepository.saveAll(records);
    this.ptsRecordRepository.flush();

    try {
      var file = new PtsFileEntity();
      file.setSetor(normalizedSetor);
      file.setFileName(filePath.getFileName().toString());
      file.setPath(filePath.toString());
      file.setSize(Files.size(filePath));
      file.setLastModified(LocalDateTime.ofInstant(Files.getLastModifiedTime(filePath).toInstant(), ZoneId.systemDefault()));
      this.ptsFileRepository.save(file);
      this.ptsFileRepository.flush();
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao registrar arquivo PTS: " + e.getMessage());
    }
  }

  private List<PtsRecordEntity> readRecords(Path filePath, String setor) {
    try (InputStream inputStream = Files.newInputStream(filePath);
        XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
      var records = new ArrayList<PtsRecordEntity>();

      for (Map.Entry<String, String> sheetEntry : SHEET_MAP.entrySet()) {
        Sheet sheet = findSheet(workbook, sheetEntry.getKey(), sheetEntry.getValue());
        if (sheet == null) {
          log.info("PTS import: aba '{}' nao encontrada no arquivo {}", sheetEntry.getKey(), filePath);
          continue;
        }

        int headerIndex = findHeaderRowIndex(sheet);
        var columnMap = readHeaderMap(sheet, headerIndex);
        validateRequiredColumns(columnMap, sheet.getSheetName());

        String currentEtapa = "";
        for (int rowIndex = headerIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
          Row row = sheet.getRow(rowIndex);
          if (isEmptyRow(row)) {
            continue;
          }

          var etapaValue = readMapped(row, columnMap, "etapa");
          if (!isBlankLike(etapaValue)) {
            currentEtapa = etapaValue;
          }

          var variavel = readMapped(row, columnMap, "variavel");
          if (isBlankLike(variavel)) {
            continue;
          }

          var record = new PtsRecordEntity();
          record.setSetor(setor);
          record.setProduto(sheetEntry.getValue());
          record.setEtapa(cleanValue(currentEtapa));
          record.setItem(cleanValue(readMapped(row, columnMap, "item")));
          record.setVariavel(cleanValue(variavel));
          record.setClassificacao(cleanValue(readMapped(row, columnMap, "classificacao")));
          record.setUnidade(cleanValue(readMapped(row, columnMap, "unidade")));
          record.setLimiteInf(cleanValue(readMapped(row, columnMap, "limite_inf")));
          record.setLimiteSup(cleanValue(readMapped(row, columnMap, "limite_sup")));
          record.setRespColeta(cleanValue(readMapped(row, columnMap, "resp_coleta")));
          record.setRespAnalise(cleanValue(readMapped(row, columnMap, "resp_analise")));
          record.setFrequencia(cleanValue(readMapped(row, columnMap, "frequencia")));
          record.setPontoColeta(cleanValue(readMapped(row, columnMap, "ponto_coleta")));
          record.setAmostra(cleanValue(readMapped(row, columnMap, "amostra")));
          record.setMetodoAnalise(cleanValue(readMapped(row, columnMap, "metodo_analise")));
          record.setTag(cleanValue(readMapped(row, columnMap, "tag")));
          record.setTagAspen(cleanValue(readMapped(row, columnMap, "tag_aspen")));
          record.setAcaoAbaixo(cleanValue(readMapped(row, columnMap, "acao_abaixo")));
          record.setAcaoAcima(cleanValue(readMapped(row, columnMap, "acao_acima")));
          record.setFca(cleanValue(readMapped(row, columnMap, "fca")));
          record.setVaiNoApp(cleanValue(readMapped(row, columnMap, "vai_no_app")));
          record.setDocumentoReferencia(cleanValue(readMapped(row, columnMap, "documento_referencia")));
          records.add(record);
        }
      }

      if (records.isEmpty()) {
        log.info("PTS import: nenhuma linha estruturada encontrada em {}. Tentando fallback pts_data.json", filePath);
        records.addAll(readLegacyJsonFallback(filePath, setor));
      }

      log.info("PTS import: {} registros montados para setor {}", records.size(), setor);
      return records;
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao ler planilha PTS: " + e.getMessage());
    }
  }

  private List<PtsRecordEntity> readLegacyJsonFallback(Path filePath, String setor) {
    try {
      String jsonContent = readLegacyJsonContent(filePath);
      if (jsonContent == null || jsonContent.isBlank()) {
        return List.of();
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> root = this.jsonParser.parseMap(jsonContent);
      Object rowsRaw = root.get("rows");
      if (!(rowsRaw instanceof List<?> rows)) {
        return List.of();
      }

      var records = new ArrayList<PtsRecordEntity>();
      for (Object rowRaw : rows) {
        if (!(rowRaw instanceof Map<?, ?> genericRow)) {
          continue;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) genericRow;
        var record = new PtsRecordEntity();
        record.setSetor(setor);
        record.setProduto(cleanValue(asText(row.get("produto"))));
        record.setEtapa(cleanValue(asText(row.get("etapa"))));
        record.setItem(cleanValue(asText(row.get("item"))));
        record.setVariavel(cleanValue(asText(row.get("variavel"))));
        record.setClassificacao(cleanValue(asText(row.get("classificacao"))));
        record.setUnidade(cleanValue(asText(row.get("unidade"))));
        record.setLimiteInf(cleanValue(asText(row.get("limite_inf"))));
        record.setLimiteSup(cleanValue(asText(row.get("limite_sup"))));
        record.setRespColeta(cleanValue(asText(row.get("resp_coleta"))));
        record.setRespAnalise(cleanValue(asText(row.get("resp_analise"))));
        record.setFrequencia(cleanValue(asText(row.get("frequencia"))));
        record.setPontoColeta(cleanValue(asText(row.get("ponto_coleta"))));
        record.setAmostra(cleanValue(asText(row.get("amostra"))));
        record.setMetodoAnalise(cleanValue(asText(row.get("metodo_analise"))));
        record.setTag(cleanValue(asText(row.get("tag"))));
        record.setTagAspen(cleanValue(asText(row.get("tag_aspen"))));
        record.setAcaoAbaixo(cleanValue(asText(row.get("acao_abaixo"))));
        record.setAcaoAcima(cleanValue(asText(row.get("acao_acima"))));
        record.setFca(cleanValue(asText(row.get("fca"))));
        record.setVaiNoApp(cleanValue(asText(row.get("vai_no_app"))));
        record.setDocumentoReferencia(cleanValue(asText(row.get("documento_referencia"))));
        records.add(record);
      }

      log.info("PTS import fallback: {} registros carregados da base legada", records.size());
      return records;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Falha ao ler base legada do PTS: " + exception.getMessage());
    }
  }

  private String readLegacyJsonContent(Path filePath) throws IOException {
    Path dataDir = filePath.getParent() != null ? filePath.getParent().getParent() : null;
    if (dataDir != null) {
      Path jsonPath = dataDir.resolve("pts_data.json");
      if (Files.exists(jsonPath)) {
        return Files.readString(jsonPath, StandardCharsets.UTF_8);
      }
      log.warn("PTS import fallback: arquivo legado nao encontrado em {}", jsonPath);
    }

    var resource = new ClassPathResource("pts_data.json");
    if (resource.exists()) {
      return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    log.warn("PTS import fallback: recurso pts_data.json nao encontrado no classpath");
    return null;
  }

  private Map<String, Integer> readHeaderMap(Sheet sheet, int headerIndex) {
    Row headerRow = sheet.getRow(headerIndex);
    if (headerRow == null) {
      return Map.of();
    }

    Row prevRow = headerIndex > 0 ? sheet.getRow(headerIndex - 1) : null;
    var columns = new LinkedHashMap<String, Integer>();
    int maxCols = Math.max((int) headerRow.getLastCellNum(), 25);

    for (int colIndex = 0; colIndex < maxCols; colIndex++) {
      Cell cell = headerRow.getCell(colIndex);
      String rawHeader = cell != null ? normalizeHeader(dataFormatter.formatCellValue(cell)) : "";

      // 1. Se a celula na linha do cabecalho estiver em branco, verificar a linha anterior (ex: B6 "Etapa do Processo" ou U6 "Documento referencia")
      if (rawHeader.isBlank() && prevRow != null) {
        Cell prevCell = prevRow.getCell(colIndex);
        if (prevCell != null) {
          rawHeader = normalizeHeader(dataFormatter.formatCellValue(prevCell));
        }
      }

      // 2. Se ainda estiver em branco, verificar se faz parte de regiao mesclada (merged region)
      if (rawHeader.isBlank()) {
        rawHeader = normalizeHeader(getMergedCellValue(sheet, headerIndex, colIndex));
      }

      if (rawHeader.isBlank()) {
        continue;
      }

      columns.put(COLUMN_RENAME.getOrDefault(rawHeader, rawHeader), colIndex);
    }

    // 3. Fallbacks para garantir que "etapa" e "documento_referencia" sejam mapeados
    if (!columns.containsKey("etapa")) {
      Integer itemIndex = columns.get("item");
      if (itemIndex != null && itemIndex > 0) {
        columns.put("etapa", itemIndex - 1);
      } else {
        columns.put("etapa", 1);
      }
    }

    if (!columns.containsKey("documento_referencia")) {
      Integer vaiNoAppIndex = columns.get("vai_no_app");
      if (vaiNoAppIndex != null) {
        columns.put("documento_referencia", vaiNoAppIndex + 1);
      } else {
        columns.put("documento_referencia", 20);
      }
    }

    return columns;
  }

  private String getMergedCellValue(Sheet sheet, int rowIndex, int colIndex) {
    for (CellRangeAddress range : sheet.getMergedRegions()) {
      if (range.isInRange(rowIndex, colIndex)) {
        Row r = sheet.getRow(range.getFirstRow());
        if (r != null) {
          Cell c = r.getCell(range.getFirstColumn());
          if (c != null) {
            return dataFormatter.formatCellValue(c);
          }
        }
      }
    }
    return "";
  }

  private int findHeaderRowIndex(Sheet sheet) {
    Row defaultHeader = sheet.getRow(HEADER_ROW_INDEX);
    if (defaultHeader != null && rowContainsAny(defaultHeader, "item", "variavel")) {
      return HEADER_ROW_INDEX;
    }
    for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 15); r++) {
      Row row = sheet.getRow(r);
      if (row != null && rowContainsAny(row, "item", "variavel")) {
        return r;
      }
    }
    return HEADER_ROW_INDEX;
  }

  private boolean rowContainsAny(Row row, String... keywords) {
    for (Cell cell : row) {
      String text = normalizeHeader(dataFormatter.formatCellValue(cell));
      for (String kw : keywords) {
        if (text.contains(kw)) {
          return true;
        }
      }
    }
    return false;
  }

  private Sheet findSheet(Workbook workbook, String targetKey, String productName) {
    Sheet sheet = workbook.getSheet(targetKey);
    if (sheet != null) {
      return sheet;
    }
    String normTarget = normalizeSheetName(targetKey);
    String normProduct = normalizeSheetName(productName);
    for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
      Sheet s = workbook.getSheetAt(i);
      String normName = normalizeSheetName(s.getSheetName());
      if (normName.equals(normTarget) || normName.equals(normProduct) || normName.contains(normProduct)) {
        return s;
      }
    }
    return null;
  }

  private String normalizeSheetName(String name) {
    if (name == null) {
      return "";
    }
    return Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^a-zA-Z0-9]", "")
        .toLowerCase(Locale.ROOT);
  }

  private void validateRequiredColumns(Map<String, Integer> columnMap, String sheetName) {
    var missing = REQUIRED_COLUMNS.stream()
        .filter(required -> !columnMap.containsKey(required))
        .toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "Colunas obrigatorias ausentes na aba '" + sheetName + "': " + String.join(", ", missing));
    }
  }

  private String readMapped(Row row, Map<String, Integer> columnMap, String field) {
    Integer index = columnMap.get(field);
    if (index == null) {
      return "-";
    }
    return dataFormatter.formatCellValue(row.getCell(index)).trim();
  }

  private boolean isEmptyRow(Row row) {
    if (row == null) {
      return true;
    }
    for (Cell cell : row) {
      if (!dataFormatter.formatCellValue(cell).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private boolean isBlankLike(String value) {
    if (value == null) {
      return true;
    }
    var trimmed = value.trim();
    return trimmed.isEmpty() || trimmed.equalsIgnoreCase("nan");
  }

  private String cleanValue(String value) {
    if (isBlankLike(value)) {
      return "-";
    }
    return PtsTextSanitizer.sanitize(value).trim();
  }

  private String asText(Object value) {
    return value == null ? "-" : PtsTextSanitizer.sanitize(String.valueOf(value));
  }

  private String normalizeHeader(String value) {
    return Normalizer.normalize(PtsTextSanitizer.sanitize(String.valueOf(value)), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .trim()
        .toLowerCase(Locale.ROOT);
  }
}


