package com.wlilan.backend_assistent.pts;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.usuario.UsuarioEntity;

@RestController
@RequestMapping("/api/pts")
public class PtsController {

  private final PtsQueryService ptsQueryService;

  public PtsController(PtsQueryService ptsQueryService) {
    this.ptsQueryService = ptsQueryService;
  }

  @GetMapping("/products")
  public ResponseEntity<List<String>> getProducts(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.ptsQueryService.getProducts(usuario.getSetorAtivo()));
  }

  @GetMapping("/items")
  public ResponseEntity<List<String>> getItems(
      @RequestParam("product") String product,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.ptsQueryService.getItems(usuario.getSetorAtivo(), product));
  }

  @GetMapping("/data")
  public ResponseEntity<List<PtsRecordEntity>> getData(
      @RequestParam("product") String product,
      @RequestParam(value = "item", required = false) String item,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.ptsQueryService.getData(usuario.getSetorAtivo(), product, item));
  }

  @GetMapping("/files")
  public ResponseEntity<List<PtsFileDTO>> getFiles(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.ptsQueryService.getFiles(usuario.getSetorAtivo()));
  }

  @DeleteMapping("/files/current")
  public ResponseEntity<Map<String, String>> deleteCurrentFile(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    this.ptsQueryService.deleteCurrentFile(usuario.getSetorAtivo());
    return ResponseEntity.ok(Map.of("message", "Arquivo PTS excluido com sucesso."));
  }
}

