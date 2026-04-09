package com.wlilan.backend_assistent.usuario.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.DTO.AuthDTO;
import com.wlilan.backend_assistent.DTO.TokenResponseDTO;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.useCases.AuthUseCase;
import com.wlilan.backend_assistent.usuario.useCases.ServiceUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthUseCase authUseCase;
  private final ServiceUseCase serviceUseCase;

  public AuthController(AuthUseCase authUseCase, ServiceUseCase serviceUseCase) {
    this.authUseCase = authUseCase;
    this.serviceUseCase = serviceUseCase;
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody AuthDTO authDTO) {
    var response = this.authUseCase.execute(authDTO);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/register")
  public ResponseEntity<UsuarioEntity> register(@Valid @RequestBody UsuarioEntity usuario) {
    var result = this.serviceUseCase.execute(usuario);
    return ResponseEntity.ok(result);
  }
}
