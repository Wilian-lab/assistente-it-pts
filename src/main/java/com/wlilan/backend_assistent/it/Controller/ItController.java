package com.wlilan.backend_assistent.it.Controller;

import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.it.it.usecases.CreateItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.DeleteItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.GetAllItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.GetItByIdUseCase;
import com.wlilan.backend_assistent.it.it.usecases.UpdateItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.UpdateItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.UploadItFileUseCase;
import com.wlilan.backend_assistent.it.it.usecases.UploadItPdfCommand;
import com.wlilan.backend_assistent.pts.PtsImportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/it")
public class ItController {

  private final CreateItUseCase createItUseCase;
  private final GetAllItUseCase getAllItUseCase;
  private final GetItByIdUseCase getItByIdUseCase;
  private final DeleteItUseCase deleteItUseCase;
  private final UpdateItUseCase updateItUseCase;
  private final UploadItFileUseCase uploadItFileUseCase;
  private final PtsImportService ptsImportService;
  private final Path itStorageDirectory;

  public ItController(
      CreateItUseCase createItUseCase,
      GetAllItUseCase getAllItUseCase,
      GetItByIdUseCase getItByIdUseCase,
      DeleteItUseCase deleteItUseCase,
      UpdateItUseCase updateItUseCase,
      UploadItFileUseCase uploadItFileUseCase,
      PtsImportService ptsImportService,
      @Value("${app.storage.it-dir}") String itStorageDirectory) {
    this.createItUseCase = createItUseCase;
    this.getAllItUseCase = getAllItUseCase;
    this.getItByIdUseCase = getItByIdUseCase;
    this.deleteItUseCase = deleteItUseCase;
    this.updateItUseCase = updateItUseCase;
    this.uploadItFileUseCase = uploadItFileUseCase;
    this.ptsImportService = ptsImportService;
    this.itStorageDirectory = Paths.get(itStorageDirectory);
  }

  @PostMapping
  public ResponseEntity<ItEntity> create(@Valid @RequestBody ItEntity itEntity, Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var result = this.createItUseCase.execute(itEntity, usuario.getSetorAtivo());
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping
  public ResponseEntity<Iterable<ItEntity>> findAll(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var result = this.getAllItUseCase.execute(usuario.getSetorAtivo());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ItEntity> findById(@PathVariable UUID id, Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var result = this.getItByIdUseCase.execute(id, usuario.getSetorAtivo());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}/file")
  public ResponseEntity<Resource> openFile(@PathVariable UUID id, Authentication authentication) throws Exception {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var it = this.getItByIdUseCase.execute(id, usuario.getSetorAtivo());

    var resolvedPath = resolveExistingItFile(it);
    if (resolvedPath == null) {
      return ResponseEntity.notFound().build();
    }

    if (!resolvedPath.toString().equals(String.valueOf(it.getFileUrl()))) {
      it.setFileUrl(resolvedPath.toString());
      this.updateItUseCase.execute(it.getId(), it, usuario.getSetorAtivo());
    }

    var resource = new FileSystemResource(resolvedPath);
    var contentType = Files.probeContentType(resolvedPath);
    var fileName = resolvedPath.getFileName().toString();

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_PDF)
        .body(resource);
  }

  @PutMapping("/{id}")
  public ResponseEntity<ItEntity> update(@PathVariable UUID id, @Valid @RequestBody ItEntity itEntity, Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var result = this.updateItUseCase.execute(id, itEntity, usuario.getSetorAtivo());
    return ResponseEntity.ok(result);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id, Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    this.deleteItUseCase.execute(id, usuario.getSetorAtivo());
    return ResponseEntity.ok(Map.of("message", "IT deletada com sucesso"));
  }

  @PostMapping("/upload/pts")
  public ResponseEntity<Map<String, String>> uploadPts(
      @RequestParam("file") MultipartFile file,
      @RequestParam("setor") String setor,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var setorAtivo = validateAdminSetor(setor, usuario);
    var savedPath = this.uploadItFileUseCase.uploadPtsExcel(file, setorAtivo);
    this.ptsImportService.importFile(Path.of(savedPath), setorAtivo);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "message", "Planilha PTS enviada com sucesso"));
  }

  @PostMapping("/upload/pdf")
  public ResponseEntity<Map<String, String>> uploadPdf(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam("setor") String setor,
      @RequestParam(value = "existingItId", required = false) UUID existingItId,
      @RequestParam(value = "documento", required = false) String documento,
      @RequestParam(value = "revisao", required = false) String revisao,
      @RequestParam(value = "dataPublicacao", required = false) LocalDateTime dataPublicacao,
      @RequestParam(value = "paginaAtual", required = false) Integer paginaAtual,
      @RequestParam(value = "totalPaginas", required = false) Integer totalPaginas,
      @RequestParam(value = "prazoTreinamentoDias", required = false) Integer prazoTreinamentoDias,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var setorAtivo = validateAdminSetor(setor, usuario);
    var savedPath = this.uploadItFileUseCase.uploadItPdf(new UploadItPdfCommand(
        file,
        setorAtivo,
        status,
        existingItId,
        documento,
        revisao,
        dataPublicacao,
        paginaAtual,
        totalPaginas,
        prazoTreinamentoDias));
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "message", "Arquivo PDF enviado com sucesso"));
  }

  @PostMapping("/sync")
  public ResponseEntity<Map<String, Object>> syncFiles(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var synced = this.uploadItFileUseCase.syncExistingPdfs(usuario.getSetorAtivo());
    return ResponseEntity.ok(Map.of(
        "message", "Sincronizacao concluida. " + synced + " IT(s) processada(s) para o setor ativo.",
        "count", synced,
        "setor", usuario.getSetorAtivo()));
  }

  private String validateAdminSetor(String setorInformado, UsuarioEntity usuario) {
    if (usuario != null && usuario.getRole() == com.wlilan.backend_assistent.usuario.Role.SUPER_ADMIN) {
      return com.wlilan.backend_assistent.Security.SetorSupport.normalize(setorInformado);
    }
    var setorAtivo = usuario == null ? "" : usuario.getSetorAtivo();
    var normalizedSetorAtivo = com.wlilan.backend_assistent.Security.SetorSupport.normalize(setorAtivo);
    var normalizedSetorInformado = com.wlilan.backend_assistent.Security.SetorSupport.normalize(setorInformado);
    if (!normalizedSetorAtivo.equals(normalizedSetorInformado)) {
      throw new IllegalArgumentException("O setor informado nao corresponde ao setor ativo do administrador.");
    }
    return normalizedSetorAtivo;
  }

  private Path resolveExistingItFile(ItEntity it) {
    var rawFileUrl = String.valueOf(it.getFileUrl() == null ? "" : it.getFileUrl()).trim();
    if (rawFileUrl.isBlank()) {
      return null;
    }

    var directPath = toSafePath(rawFileUrl);
    if (directPath != null && Files.exists(directPath)) {
      return directPath;
    }

    var fileName = extractFileName(rawFileUrl);
    if (fileName.isBlank()) {
      return null;
    }

    var setorDirectory = this.itStorageDirectory.resolve(
        com.wlilan.backend_assistent.Security.SetorSupport.normalize(it.getSetor()));
    var directSectorFile = setorDirectory.resolve(fileName);
    if (Files.exists(directSectorFile)) {
      return directSectorFile;
    }

    try (Stream<Path> paths = Files.walk(this.itStorageDirectory, 4)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
          .findFirst()
          .orElse(null);
    } catch (Exception exception) {
      return null;
    }
  }

  private Path toSafePath(String rawFileUrl) {
    try {
      var normalized = rawFileUrl.replace('\\', '/');
      return Paths.get(normalized);
    } catch (Exception exception) {
      return null;
    }
  }

  private String extractFileName(String rawFileUrl) {
    var normalized = rawFileUrl.replace('\\', '/').trim();
    var lastSlash = normalized.lastIndexOf('/');
    var fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    return fileName.trim();
  }
}
