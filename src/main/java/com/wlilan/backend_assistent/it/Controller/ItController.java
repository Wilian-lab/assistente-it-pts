package com.wlilan.backend_assistent.it.Controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import com.wlilan.backend_assistent.it.it.usecases.CreateItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.DeleteItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.GetAllItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.GetItByIdUseCase;
import com.wlilan.backend_assistent.it.it.usecases.UpdateItUseCase;
import com.wlilan.backend_assistent.it.it.usecases.UploadItFileUseCase;

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

  public ItController(
      CreateItUseCase createItUseCase,
      GetAllItUseCase getAllItUseCase,
      GetItByIdUseCase getItByIdUseCase,
      DeleteItUseCase deleteItUseCase,
      UpdateItUseCase updateItUseCase,
      UploadItFileUseCase uploadItFileUseCase) {
    this.createItUseCase = createItUseCase;
    this.getAllItUseCase = getAllItUseCase;
    this.getItByIdUseCase = getItByIdUseCase;
    this.deleteItUseCase = deleteItUseCase;
    this.updateItUseCase = updateItUseCase;
    this.uploadItFileUseCase = uploadItFileUseCase;
  }

  @PostMapping
  public ResponseEntity<ItEntity> create(@Valid @RequestBody ItEntity itEntity) {
    var result = this.createItUseCase.execute(itEntity);
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @GetMapping
  public ResponseEntity<Iterable<ItEntity>> findAll() {
    var result = this.getAllItUseCase.execute();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ItEntity> findById(@PathVariable UUID id) {
    var result = this.getItByIdUseCase.execute(id);
    return ResponseEntity.ok(result);
  }

  @PutMapping("/{id}")
  public ResponseEntity<ItEntity> update(@PathVariable UUID id, @Valid @RequestBody ItEntity itEntity) {
    var result = this.updateItUseCase.execute(id, itEntity);
    return ResponseEntity.ok(result);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id) {
    this.deleteItUseCase.execute(id);
    return ResponseEntity.ok(Map.of("message", "IT deletada com sucesso"));
  }

  @PostMapping("/upload/pts")
  public ResponseEntity<Map<String, String>> uploadPts(@RequestParam("file") MultipartFile file) {
    var savedPath = this.uploadItFileUseCase.uploadPtsExcel(file);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "message", "Planilha PTS enviada com sucesso",
        "path", savedPath));
  }

  @PostMapping("/upload/pdf")
  public ResponseEntity<Map<String, String>> uploadPdf(@RequestParam("file") MultipartFile file) {
    var savedPath = this.uploadItFileUseCase.uploadItPdf(file);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "message", "Arquivo PDF enviado com sucesso",
        "path", savedPath));
  }
}
