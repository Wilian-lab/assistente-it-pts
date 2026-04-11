package com.wlilan.backend_assistent.usuario.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.useCases.ServiceUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuario")
public class UsuarioController {

  private final ServiceUseCase serviceUseCase;

  public UsuarioController(ServiceUseCase serviceUseCase) {
    this.serviceUseCase = serviceUseCase;
  }

  @PostMapping
  public ResponseEntity<UsuarioEntity> create(@Valid @RequestBody UsuarioEntity usuario) {
    var result = this.serviceUseCase.execute(usuario);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/me")
  public ResponseEntity<UsuarioEntity> getCurrentUser(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(usuario);
  }

  @GetMapping
  public ResponseEntity<Iterable<UsuarioEntity>> getAll(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var result = this.serviceUseCase.getAllBySetor(usuario.getSetorAtivo());
    return ResponseEntity.ok(result);
  }
}
