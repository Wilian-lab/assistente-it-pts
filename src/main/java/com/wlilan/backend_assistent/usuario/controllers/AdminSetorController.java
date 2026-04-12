package com.wlilan.backend_assistent.usuario.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.DTO.SetorRequestDTO;
import com.wlilan.backend_assistent.usuario.SetorEntity;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.useCases.ServiceUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/setores")
public class AdminSetorController {

  private final ServiceUseCase serviceUseCase;

  public AdminSetorController(ServiceUseCase serviceUseCase) {
    this.serviceUseCase = serviceUseCase;
  }

  @GetMapping
  public ResponseEntity<Iterable<SetorEntity>> list(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.serviceUseCase.getVisibleSetores(usuario));
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(
      @Valid @RequestBody SetorRequestDTO request,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var setor = this.serviceUseCase.createSetor(request.codigo(), usuario);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "id", setor.getId(),
        "codigo", setor.getCodigo()));
  }
}
