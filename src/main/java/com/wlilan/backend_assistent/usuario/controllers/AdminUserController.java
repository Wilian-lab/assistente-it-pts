package com.wlilan.backend_assistent.usuario.controllers;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.DTO.AdminCreateUserDTO;
import com.wlilan.backend_assistent.DTO.UserTrainingDTO;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.useCases.ServiceUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final ServiceUseCase serviceUseCase;

  public AdminUserController(ServiceUseCase serviceUseCase) {
    this.serviceUseCase = serviceUseCase;
  }

  @GetMapping
  public ResponseEntity<Iterable<UsuarioEntity>> listAll(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.serviceUseCase.getAllBySetor(usuario.getSetorAtivo()));
  }

  @PostMapping
  public ResponseEntity<UsuarioEntity> create(@Valid @RequestBody AdminCreateUserDTO userDTO) {
    var created = this.serviceUseCase.executeAdminCreate(userDTO);
    return ResponseEntity.ok(created);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    this.serviceUseCase.deleteById(id, usuario.getSetorAtivo());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/training")
  public ResponseEntity<UsuarioEntity> updateTraining(
      @PathVariable UUID id,
      @Valid @RequestBody UserTrainingDTO trainingDTO,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var updated = this.serviceUseCase.updateTraining(id, trainingDTO, usuario.getSetorAtivo());
    return ResponseEntity.ok(updated);
  }
}
