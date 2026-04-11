package com.wlilan.backend_assistent.pts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.Security.SetorSupport;

import jakarta.transaction.Transactional;

@Service
public class PtsQueryService {

  private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

  private final PtsFileRepository ptsFileRepository;
  private final PtsRecordRepository ptsRecordRepository;
  private final PtsImportService ptsImportService;

  public PtsQueryService(
      PtsFileRepository ptsFileRepository,
      PtsRecordRepository ptsRecordRepository,
      PtsImportService ptsImportService) {
    this.ptsFileRepository = ptsFileRepository;
    this.ptsRecordRepository = ptsRecordRepository;
    this.ptsImportService = ptsImportService;
  }

  public List<String> getProducts(String setor) {
    var normalizedSetor = SetorSupport.normalize(setor);
    ensureHealthyData(normalizedSetor);
    return this.ptsRecordRepository.findAllBySetorOrderByProdutoAscItemAscVariavelAsc(normalizedSetor).stream()
        .map(PtsRecordEntity::getProduto)
        .map(PtsTextSanitizer::sanitize)
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  public List<String> getItems(String setor, String product) {
    var normalizedSetor = SetorSupport.normalize(setor);
    ensureHealthyData(normalizedSetor);
    var safeProduct = PtsTextSanitizer.sanitize(product);
    return this.ptsRecordRepository.findAllBySetorOrderByProdutoAscItemAscVariavelAsc(normalizedSetor).stream()
        .filter(record -> sameValue(record.getProduto(), safeProduct))
        .map(PtsRecordEntity::getItem)
        .map(PtsTextSanitizer::sanitize)
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  public List<PtsRecordEntity> getData(String setor, String product, String item) {
    var normalizedSetor = SetorSupport.normalize(setor);
    ensureHealthyData(normalizedSetor);
    var safeProduct = PtsTextSanitizer.sanitize(product);
    var safeItem = PtsTextSanitizer.sanitize(item);
    return this.ptsRecordRepository.findAllBySetorOrderByProdutoAscItemAscVariavelAsc(normalizedSetor).stream()
        .filter(record -> sameValue(record.getProduto(), safeProduct))
        .filter(record -> !hasText(safeItem) || sameValue(record.getItem(), safeItem))
        .map(this::sanitizeRecord)
        .toList();
  }

  public List<PtsFileDTO> getFiles() {
    return this.ptsFileRepository.findAllByOrderBySetorAsc().stream()
        .map(file -> PtsFileDTO.builder()
            .setor(PtsTextSanitizer.sanitize(file.getSetor()))
            .fileName(PtsTextSanitizer.sanitize(file.getFileName()))
            .path(PtsTextSanitizer.sanitize(file.getPath()))
            .size(file.getSize() == null ? 0L : file.getSize())
            .lastModified(file.getLastModified() == null ? "-" : file.getLastModified().format(FILE_DATE_FORMAT))
            .recordsCount(this.ptsRecordRepository.countBySetor(file.getSetor()))
            .build())
        .toList();
  }

  public List<PtsFileDTO> getFiles(String setor) {
    var normalizedSetor = SetorSupport.normalize(setor);
    return this.ptsFileRepository.findBySetor(normalizedSetor)
        .map(file -> List.of(PtsFileDTO.builder()
            .setor(PtsTextSanitizer.sanitize(file.getSetor()))
            .fileName(PtsTextSanitizer.sanitize(file.getFileName()))
            .path(PtsTextSanitizer.sanitize(file.getPath()))
            .size(file.getSize() == null ? 0L : file.getSize())
            .lastModified(file.getLastModified() == null ? "-" : file.getLastModified().format(FILE_DATE_FORMAT))
            .recordsCount(this.ptsRecordRepository.countBySetor(file.getSetor()))
            .build()))
        .orElse(List.of());
  }

  @Transactional
  public void deleteCurrentFile(String setor) {
    var normalizedSetor = SetorSupport.normalize(setor);
    this.ptsFileRepository.findBySetor(normalizedSetor).ifPresent(file -> {
      if (file.getPath() != null && !file.getPath().isBlank()) {
        try {
          Files.deleteIfExists(Path.of(file.getPath()));
        } catch (Exception exception) {
          throw new RuntimeException("Falha ao excluir arquivo PTS do disco.");
        }
      }
    });
    this.ptsRecordRepository.deleteBySetor(normalizedSetor);
    this.ptsFileRepository.deleteBySetor(normalizedSetor);
  }

  private void ensureHealthyData(String setor) {
    var sample = this.ptsRecordRepository.findTop20BySetorOrderByProdutoAscItemAscVariavelAsc(setor);
    if (sample.isEmpty()) {
      return;
    }
    boolean suspicious = sample.stream().anyMatch(this::hasSuspiciousEncoding);
    if (!suspicious) {
      return;
    }

    this.ptsFileRepository.findBySetor(setor).ifPresent(file -> {
      var path = firstNonBlank(file.getPath());
      if (path.isBlank()) {
        return;
      }
      try {
        var filePath = Path.of(path);
        if (Files.exists(filePath)) {
          this.ptsImportService.importFile(filePath, setor);
        }
      } catch (Exception ignored) {
        // fallback to sanitized response if reimport fails
      }
    });
  }

  private boolean hasSuspiciousEncoding(PtsRecordEntity record) {
    return List.of(
        record.getProduto(),
        record.getEtapa(),
        record.getItem(),
        record.getVariavel(),
        record.getClassificacao(),
        record.getUnidade(),
        record.getRespColeta(),
        record.getRespAnalise(),
        record.getFrequencia(),
        record.getPontoColeta(),
        record.getMetodoAnalise(),
        record.getAcaoAbaixo(),
        record.getAcaoAcima(),
        record.getDocumentoReferencia())
        .stream()
        .filter(Objects::nonNull)
        .anyMatch(PtsTextSanitizer::looksMisencoded);
  }

  private PtsRecordEntity sanitizeRecord(PtsRecordEntity source) {
    var target = new PtsRecordEntity();
    target.setId(source.getId());
    target.setSetor(safe(source.getSetor()));
    target.setProduto(safe(source.getProduto()));
    target.setEtapa(safe(source.getEtapa()));
    target.setItem(safe(source.getItem()));
    target.setVariavel(safe(source.getVariavel()));
    target.setClassificacao(safe(source.getClassificacao()));
    target.setUnidade(safe(source.getUnidade()));
    target.setLimiteInf(safe(source.getLimiteInf()));
    target.setLimiteSup(safe(source.getLimiteSup()));
    target.setRespColeta(safe(source.getRespColeta()));
    target.setRespAnalise(safe(source.getRespAnalise()));
    target.setFrequencia(safe(source.getFrequencia()));
    target.setPontoColeta(safe(source.getPontoColeta()));
    target.setAmostra(safe(source.getAmostra()));
    target.setMetodoAnalise(safe(source.getMetodoAnalise()));
    target.setTag(safe(source.getTag()));
    target.setTagAspen(safe(source.getTagAspen()));
    target.setAcaoAbaixo(safe(source.getAcaoAbaixo()));
    target.setAcaoAcima(safe(source.getAcaoAcima()));
    target.setFca(safe(source.getFca()));
    target.setVaiNoApp(safe(source.getVaiNoApp()));
    target.setDocumentoReferencia(safe(source.getDocumentoReferencia()));
    return target;
  }

  private boolean sameValue(String databaseValue, String requestValue) {
    var left = normalize(databaseValue);
    var right = normalize(requestValue);
    return hasText(left) && hasText(right) && left.equals(right);
  }

  private String normalize(String value) {
    return safe(value).toLowerCase(Locale.ROOT).trim();
  }

  private String safe(String value) {
    return PtsTextSanitizer.sanitize(firstNonBlank(value));
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }
}
