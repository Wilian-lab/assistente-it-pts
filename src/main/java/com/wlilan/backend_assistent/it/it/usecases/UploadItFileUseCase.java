package com.wlilan.backend_assistent.it.it.usecases;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadItFileUseCase {

  private final Path ptsExcelPath;
  private final Path itPdfDirectory;

  public UploadItFileUseCase(
      @Value("${app.storage.pts-excel-path}") String ptsExcelPath,
      @Value("${app.storage.it-dir}") String itPdfDirectory) {
    this.ptsExcelPath = Paths.get(ptsExcelPath);
    this.itPdfDirectory = Paths.get(itPdfDirectory);
  }

  public String uploadPtsExcel(MultipartFile file) {
    validateFile(file, ".xlsx", "Planilha PTS");
    try {
      var parent = this.ptsExcelPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      saveWithReplace(file, this.ptsExcelPath);
      return this.ptsExcelPath.toString();
    } catch (FileSystemException e) {
      throw new IllegalArgumentException(
          "Falha ao salvar planilha PTS: arquivo em uso por outro processo. Feche o arquivo e tente novamente.");
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao salvar planilha PTS: " + e.getMessage());
    }
  }

  public String uploadItPdf(MultipartFile file) {
    validateFile(file, ".pdf", "Documento IT");
    try {
      Files.createDirectories(this.itPdfDirectory);
      String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
      Path destination = this.itPdfDirectory.resolve(safeName);
      saveWithReplace(file, destination);
      return destination.toString();
    } catch (FileSystemException e) {
      throw new IllegalArgumentException(
          "Falha ao salvar arquivo IT: arquivo em uso por outro processo. Feche o arquivo e tente novamente.");
    } catch (IOException e) {
      throw new IllegalArgumentException("Falha ao salvar arquivo IT: " + e.getMessage());
    }
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
      // Fallback when atomic move is unavailable on the filesystem.
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
    if (name == null || !name.toLowerCase().endsWith(expectedExtension)) {
      throw new IllegalArgumentException(label + " deve ser do tipo " + expectedExtension);
    }
  }
}
