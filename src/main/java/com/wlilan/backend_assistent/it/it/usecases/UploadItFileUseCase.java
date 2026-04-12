package com.wlilan.backend_assistent.it.it.usecases;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.assistant.AssistantDocumentIndexService;
import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.it.UserFoundException;
import com.wlilan.backend_assistent.it.it.repository.ItRepository;

@Service
public class UploadItFileUseCase {

  private final Path ptsExcelPath;
  private final Path itPdfDirectory;
  private final ItRepository itRepository;
  private final AssistantDocumentIndexService assistantDocumentIndexService;

  public UploadItFileUseCase(
      @Value("${app.storage.pts-excel-path}") String ptsExcelPath,
      @Value("${app.storage.it-dir}") String itPdfDirectory,
      ItRepository itRepository,
      AssistantDocumentIndexService assistantDocumentIndexService) {
    this.ptsExcelPath = Paths.get(ptsExcelPath);
    this.itPdfDirectory = Paths.get(itPdfDirectory);
    this.itRepository = itRepository;
    this.assistantDocumentIndexService = assistantDocumentIndexService;
  }

  public String uploadPtsExcel(MultipartFile file, String setor) {
    validateFile(file, ".xlsx", "Planilha PTS");
    try {
      var destination = buildSectorFilePath(this.ptsExcelPath, setor, this.ptsExcelPath.getFileName().toString());
      var parent = destination.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      saveWithReplace(file, destination);
      return destination.toString();
    } catch (FileSystemException e) {
      throw new IllegalArgumentException(
          "Falha ao salvar planilha PTS: arquivo em uso por outro processo. Feche o arquivo e tente novamente.");
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao salvar planilha PTS: " + e.getMessage());
    }
  }

  public String uploadItPdf(MultipartFile file, String setor, String status) {
    return uploadItPdf(new UploadItPdfCommand(
        file,
        setor,
        status,
        null,
        null,
        null,
        null,
        null,
        null,
        null));
  }

  public String uploadItPdf(UploadItPdfCommand command) {
    var file = command.file();
    validateFile(file, ".pdf", "Documento IT");
    try {
      var targetDirectory = this.itPdfDirectory.resolve(SetorSupport.normalize(command.setor()));
      Files.createDirectories(targetDirectory);
      String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
      Path destination = targetDirectory.resolve(safeName);
      saveWithReplace(file, destination);
      upsertItRecord(destination, command);
      return destination.toString();
    } catch (FileSystemException e) {
      throw new IllegalArgumentException(
          "Falha ao salvar arquivo IT: arquivo em uso por outro processo. Feche o arquivo e tente novamente.");
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao salvar arquivo IT: " + e.getMessage());
    }
  }

  public int syncExistingPdfs(String setor) {
    var normalizedSetor = SetorSupport.normalize(setor);
    if (normalizedSetor.isBlank()) {
      return 0;
    }

    var targetDirectory = this.itPdfDirectory.resolve(normalizedSetor);
    int synced = 0;

    if (Files.exists(targetDirectory)) {
      synced += syncDirectory(targetDirectory, normalizedSetor);
    }

    synced += syncLegacyRootFiles(normalizedSetor);
    return synced;
  }

  private ItEntity upsertItRecord(Path filePath, String setor, String status) {
    return upsertItRecord(filePath, new UploadItPdfCommand(
        null,
        setor,
        status,
        null,
        null,
        null,
        null,
        null,
        null,
        null));
  }

  private ItEntity upsertItRecord(Path filePath, UploadItPdfCommand command) {
    var normalizedSetor = SetorSupport.normalize(command.setor());
    var normalizedStatus = normalizeStatus(command.status());
    var fileName = filePath.getFileName().toString();
    var baseName = stripExtension(fileName);
    var fallbackDocumento = baseName;
    var fallbackTitulo = humanizeTitle(baseName);
    var metadata = this.assistantDocumentIndexService.extractDocumentMetadata(filePath, fallbackDocumento, fallbackTitulo);
    var documento = firstNonBlank(command.documento(), metadata.documento(), fallbackDocumento);
    var titulo = firstNonBlank(metadata.titulo(), fallbackTitulo, documento);
    var revisao = firstNonBlank(command.revisao(), metadata.revisao(), extractRevision(baseName));
    var safeFileUrl = filePath.toString();

    var entity = resolveTargetEntity(command.existingItId(), normalizedSetor, safeFileUrl, documento, revisao);

    if (entity.getId() != null) {
      this.itRepository.findByDocumentoAndRevisaoAndSetorAndIdNot(documento, revisao, normalizedSetor, entity.getId())
          .ifPresent(conflict -> {
            throw new UserFoundException("Ja existe uma IT cadastrada com este documento e revisao");
          });
    } else {
      this.itRepository.findByDocumentoAndRevisaoAndSetor(documento, revisao, normalizedSetor)
          .ifPresent(conflict -> {
            throw new UserFoundException("Ja existe uma IT cadastrada com este documento e revisao");
          });
    }

    entity.setDocumento(documento);
    entity.setTitulo(titulo);
    entity.setRevisao(revisao);
    entity.setStatus(normalizedStatus);
    entity.setFileUrl(safeFileUrl);
    entity.setSetor(normalizedSetor);
    entity.setDataPublicacao(resolveDateTime(command.dataPublicacao(), entity.getDataPublicacao(), LocalDateTime.now()));
    entity.setPaginaAtual(resolvePositiveInt(command.paginaAtual(), entity.getPaginaAtual(), 1));
    entity.setTotalPaginas(resolvePositiveInt(command.totalPaginas(), entity.getTotalPaginas(), 1));
    entity.setPrazoTreinamentoDias(resolveNonNegativeInt(command.prazoTreinamentoDias(), entity.getPrazoTreinamentoDias(), 365));
    validateResolvedMetadata(entity);
    var saved = this.itRepository.save(entity);
    this.assistantDocumentIndexService.ensureIndexed(saved);
    return saved;
  }

  private ItEntity resolveTargetEntity(
      java.util.UUID existingItId,
      String normalizedSetor,
      String safeFileUrl,
      String documento,
      String revisao) {
    if (existingItId != null) {
      return this.itRepository.findByIdAndSetor(existingItId, normalizedSetor)
          .orElseThrow(() -> new IllegalArgumentException("IT selecionada nao encontrada para o setor ativo"));
    }

    return this.itRepository.findByFileUrlAndSetor(safeFileUrl, normalizedSetor)
        .or(() -> this.itRepository.findByDocumentoAndRevisaoAndSetor(documento, revisao, normalizedSetor))
        .or(() -> this.itRepository.findFirstByDocumentoAndSetorOrderByDataPublicacaoDesc(documento, normalizedSetor))
        .orElseGet(ItEntity::new);
  }

  private void saveWithReplace(MultipartFile file, Path destination) throws IOException {
    Path parent = destination.getParent();
    if (parent == null) {
      throw new IOException("Destino invalido para upload");
    }

    String tempName = destination.getFileName().toString() + ".upload-tmp";
    Path tempPath = parent.resolve(tempName);

    try (var in = file.getInputStream()) {
      Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
    }

    try {
      Files.move(tempPath, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException moveError) {
      Files.move(tempPath, destination, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tempPath);
    }
  }

  private void validateFile(MultipartFile file, String expectedExtension, String label) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException(label + " vazio ou ausente");
    }
    var name = file.getOriginalFilename();
    if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
      throw new IllegalArgumentException(label + " deve ser do tipo " + expectedExtension);
    }

    var contentType = String.valueOf(file.getContentType() == null ? "" : file.getContentType()).trim().toLowerCase(Locale.ROOT);
    if (".pdf".equals(expectedExtension) && !(contentType.isBlank() || contentType.equals("application/pdf"))) {
      throw new IllegalArgumentException(label + " deve ser um PDF valido.");
    }
    if (".xlsx".equals(expectedExtension)
        && !(contentType.isBlank()
            || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            || contentType.equals("application/octet-stream"))) {
      throw new IllegalArgumentException(label + " deve ser uma planilha XLSX valida.");
    }

    try (var in = file.getInputStream()) {
      var header = in.readNBytes(8);
      if (".pdf".equals(expectedExtension) && !looksLikePdf(header)) {
        throw new IllegalArgumentException(label + " deve conter uma assinatura PDF valida.");
      }
      if (".xlsx".equals(expectedExtension) && !looksLikeZip(header)) {
        throw new IllegalArgumentException(label + " deve conter uma assinatura XLSX valida.");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao validar " + label.toLowerCase(Locale.ROOT) + ".");
    }
  }

  private boolean looksLikePdf(byte[] header) {
    return header.length >= 4
        && header[0] == '%'
        && header[1] == 'P'
        && header[2] == 'D'
        && header[3] == 'F';
  }

  private boolean looksLikeZip(byte[] header) {
    return header.length >= 4
        && header[0] == 'P'
        && header[1] == 'K'
        && (header[2] == 3 || header[2] == 5 || header[2] == 7)
        && (header[3] == 4 || header[3] == 6 || header[3] == 8);
  }

  private Path buildSectorFilePath(Path basePath, String setor, String fileName) {
    var normalizedSetor = SetorSupport.normalize(setor);
    var parent = basePath.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Destino invalido para upload");
    }
    return parent.resolve(normalizedSetor).resolve(fileName);
  }

  private String stripExtension(String fileName) {
    int extensionIndex = fileName.lastIndexOf('.');
    return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
  }

  private String humanizeTitle(String baseName) {
    return baseName.replace('_', ' ').trim();
  }

  private String extractRevision(String baseName) {
    var normalized = baseName.toUpperCase(Locale.ROOT);
    var match = java.util.regex.Pattern.compile("(?:ED|REV|R)\\s*[._-]?(\\d{1,3})").matcher(normalized);
    if (match.find()) {
      return match.group(1);
    }
    return "00";
  }

  private String normalizeStatus(String status) {
    var value = String.valueOf(status == null ? "" : status).trim();
    if (value.isBlank()) {
      return "Atualizada";
    }
    return value;
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.trim().isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private LocalDateTime resolveDateTime(LocalDateTime preferred, LocalDateTime current, LocalDateTime fallback) {
    if (preferred != null) {
      return preferred;
    }
    if (current != null) {
      return current;
    }
    return fallback;
  }

  private Integer resolvePositiveInt(Integer preferred, Integer current, Integer fallback) {
    if (preferred != null && preferred > 0) {
      return preferred;
    }
    if (current != null && current > 0) {
      return current;
    }
    return fallback;
  }

  private Integer resolveNonNegativeInt(Integer preferred, Integer current, Integer fallback) {
    if (preferred != null && preferred >= 0) {
      return preferred;
    }
    if (current != null && current >= 0) {
      return current;
    }
    return fallback;
  }

  private void validateResolvedMetadata(ItEntity entity) {
    if (entity.getDocumento() == null || entity.getDocumento().isBlank()) {
      throw new IllegalArgumentException("Documento e obrigatorio");
    }

    if (entity.getRevisao() == null || entity.getRevisao().isBlank()) {
      throw new IllegalArgumentException("Revisao e obrigatoria");
    }

    if (entity.getDataPublicacao() == null) {
      throw new IllegalArgumentException("Data de publicacao e obrigatoria");
    }

    if (entity.getPaginaAtual() == null || entity.getPaginaAtual() < 1) {
      throw new IllegalArgumentException("Pagina atual invalida");
    }

    if (entity.getTotalPaginas() == null || entity.getTotalPaginas() < 1) {
      throw new IllegalArgumentException("Total de paginas invalido");
    }

    if (entity.getPaginaAtual() > entity.getTotalPaginas()) {
      throw new IllegalArgumentException("Pagina atual nao pode ser maior que o total de paginas");
    }

    if (entity.getPrazoTreinamentoDias() == null || entity.getPrazoTreinamentoDias() < 0) {
      throw new IllegalArgumentException("Prazo de treinamento invalido");
    }
  }

  private int syncDirectory(Path directory, String setor) {
    try (Stream<Path> paths = Files.list(directory)) {
      return (int) paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
          .map(path -> upsertItRecord(path, setor, "Atualizada"))
          .count();
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao sincronizar ITs do setor: " + e.getMessage());
    }
  }

  private int syncLegacyRootFiles(String setor) {
    if (!Files.exists(this.itPdfDirectory)) {
      return 0;
    }

    try (Stream<Path> paths = Files.list(this.itPdfDirectory)) {
      return (int) paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
          .map(path -> upsertItRecord(path, setor, "Atualizada"))
          .count();
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao sincronizar ITs legadas: " + e.getMessage());
    }
  }
}
