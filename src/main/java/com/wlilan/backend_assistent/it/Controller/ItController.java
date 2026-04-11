package com.wlilan.backend_assistent.it.Controller;

import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

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

import com.wlilan.backend_assistent.it.ItEntity;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.it.it.usecases.CreateItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.DeleteItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.GetAllItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.GetItByIdUseCase;
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

  public ItController(
      CreateItUseCase createItUseCase,
      GetAllItUseCase getAllItUseCase,
      GetItByIdUseCase getItByIdUseCase,
      DeleteItUseCase deleteItUseCase,
      UpdateItUseCase updateItUseCase,
      UploadItFileUseCase uploadItFileUseCase,
      PtsImportService ptsImportService) {
    this.createItUseCase = createItUseCase;
    this.getAllItUseCase = getAllItUseCase;
    this.getItByIdUseCase = getItByIdUseCase;
    this.deleteItUseCase = deleteItUseCase;
    this.updateItUseCase = updateItUseCase;
    this.uploadItFileUseCase = uploadItFileUseCase;
    this.ptsImportService = ptsImportService;
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

    if (it.getFileUrl() == null || it.getFileUrl().isBlank()) {
      return ResponseEntity.notFound().build();
    }

    var path = Path.of(it.getFileUrl());
    if (!Files.exists(path)) {
      return ResponseEntity.notFound().build();
    }

    var resource = new FileSystemResource(path);
    var contentType = Files.probeContentType(path);
    var fileName = path.getFileName().toString();

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
      @RequestParam("setor") String setor) {
    var savedPath = this.uploadItFileUseCase.uploadPtsExcel(file, setor);
    this.ptsImportService.importFile(Path.of(savedPath), setor);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "message", "Planilha PTS enviada com sucesso",
        "path", savedPath));
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
      @RequestParam(value = "prazoTreinamentoDias", required = false) Integer prazoTreinamentoDias) {
    var savedPath = this.uploadItFileUseCase.uploadItPdf(new UploadItPdfCommand(
        file,
        setor,
        status,
        existingItId,
        documento,
        revisao,
        dataPublicacao,
        paginaAtual,
        totalPaginas,
        prazoTreinamentoDias));
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "message", "Arquivo PDF enviado com sucesso",
        "path", savedPath));
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
}
