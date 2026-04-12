package com.wlilan.backend_assistent.usuario.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.wlilan.backend_assistent.DTO.ChangePasswordDTO;
import com.wlilan.backend_assistent.DTO.UpdateProfileDTO;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.useCases.ProfileUseCase;
import com.wlilan.backend_assistent.usuario.useCases.ServiceUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuario")
public class UsuarioController {

  private final ServiceUseCase serviceUseCase;
  private final ProfileUseCase profileUseCase;

  public UsuarioController(ServiceUseCase serviceUseCase, ProfileUseCase profileUseCase) {
    this.serviceUseCase = serviceUseCase;
    this.profileUseCase = profileUseCase;
  }

  @GetMapping("/me")
  public ResponseEntity<UsuarioEntity> getCurrentUser(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(preparePublicUser(usuario));
  }

  @PutMapping("/me/profile")
  public ResponseEntity<UsuarioEntity> updateMyProfile(
      Authentication authentication,
      @Valid @RequestBody UpdateProfileDTO payload) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(preparePublicUser(this.profileUseCase.updateProfile(usuario.getId(), payload)));
  }

  @PutMapping("/me/password")
  public ResponseEntity<Void> updateMyPassword(
      Authentication authentication,
      @Valid @RequestBody ChangePasswordDTO payload) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    this.profileUseCase.changePassword(usuario.getId(), payload);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UsuarioEntity> uploadMyAvatar(
      Authentication authentication,
      @RequestPart("file") MultipartFile file) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(preparePublicUser(this.profileUseCase.updateProfileImage(usuario.getId(), file)));
  }

  @DeleteMapping("/me/avatar")
  public ResponseEntity<UsuarioEntity> removeMyAvatar(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(preparePublicUser(this.profileUseCase.removeProfileImage(usuario.getId())));
  }

  @GetMapping("/me/avatar")
  public ResponseEntity<byte[]> getMyAvatar(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    if (!usuario.hasProfileImage()) {
      return ResponseEntity.notFound().build();
    }

    var contentType = String.valueOf(usuario.getProfileImageContentType() == null ? MediaType.IMAGE_PNG_VALUE : usuario.getProfileImageContentType()).trim();
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.parseMediaType(contentType))
        .body(usuario.getProfileImageData());
  }

  @GetMapping
  public ResponseEntity<Iterable<UsuarioEntity>> getAll(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    var result = this.serviceUseCase.getAllBySetor(usuario.getSetorAtivo());
    return ResponseEntity.ok(result);
  }

  private UsuarioEntity preparePublicUser(UsuarioEntity usuario) {
    usuario.setProfileImageUrl(usuario.hasProfileImage() ? "/usuario/me/avatar" : null);
    return usuario;
  }
}
