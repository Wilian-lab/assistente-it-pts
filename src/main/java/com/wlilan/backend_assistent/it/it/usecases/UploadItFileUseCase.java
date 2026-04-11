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
    validateFile(file, ".pdf", "Documento IT");
    try {
      var targetDirectory = this.itPdfDirectory.resolve(SetorSupport.normalize(setor));
      Files.createDirectories(targetDirectory);
      String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
      Path destination = targetDirectory.resolve(safeName);
      saveWithReplace(file, destination);
      upsertItRecord(destination, setor, status);
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
    var normalizedSetor = SetorSupport.normalize(setor);
    var normalizedStatus = normalizeStatus(status);
    var fileName = filePath.getFileName().toString();
    var baseName = stripExtension(fileName);
    var documento = baseName;
    var titulo = humanizeTitle(baseName);
    var revisao = extractRevision(baseName);
    var safeFileUrl = filePath.toString();

    var entity = this.itRepository.findByFileUrlAndSetor(safeFileUrl, normalizedSetor)
        .or(() -> this.itRepository.findByDocumentoAndRevisaoAndSetor(documento, revisao, normalizedSetor))
        .orElseGet(ItEntity::new);

    entity.setDocumento(documento);
    entity.setTitulo(titulo);
    entity.setRevisao(revisao);
    entity.setStatus(normalizedStatus);
    entity.setFileUrl(safeFileUrl);
    entity.setSetor(normalizedSetor);
    entity.setDataPublicacao(LocalDateTime.now());
    entity.setPaginaAtual(1);
    entity.setTotalPaginas(1);
    entity.setPrazoTreinamentoDias(365);
    var saved = this.itRepository.save(entity);
    this.assistantDocumentIndexService.ensureIndexed(saved);
    return saved;
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
