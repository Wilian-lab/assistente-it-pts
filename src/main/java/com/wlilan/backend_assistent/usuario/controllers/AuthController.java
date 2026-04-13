package com.wlilan.backend_assistent.usuario.controllers;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.DTO.AuthDTO;
import com.wlilan.backend_assistent.DTO.ForgotPasswordRequestDTO;
import com.wlilan.backend_assistent.DTO.MessageResponseDTO;
import com.wlilan.backend_assistent.DTO.RecoveryCodeResetRequestDTO;
import com.wlilan.backend_assistent.DTO.ResetPasswordRequestDTO;
import com.wlilan.backend_assistent.DTO.SetorOptionDTO;
import com.wlilan.backend_assistent.DTO.SwitchSetorDTO;
import com.wlilan.backend_assistent.DTO.TokenResponseDTO;
import com.wlilan.backend_assistent.Security.RequestRateLimiter;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.useCases.AuthUseCase;
import com.wlilan.backend_assistent.usuario.useCases.ServiceUseCase;
import com.wlilan.backend_assistent.usuario.useCases.passwordreset.PasswordResetUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthUseCase authUseCase;
  private final ServiceUseCase serviceUseCase;
  private final PasswordResetUseCase passwordResetUseCase;
  private final RequestRateLimiter requestRateLimiter;

  public AuthController(
      AuthUseCase authUseCase,
      ServiceUseCase serviceUseCase,
      PasswordResetUseCase passwordResetUseCase,
      RequestRateLimiter requestRateLimiter) {
    this.authUseCase = authUseCase;
    this.serviceUseCase = serviceUseCase;
    this.passwordResetUseCase = passwordResetUseCase;
    this.requestRateLimiter = requestRateLimiter;
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody AuthDTO authDTO, HttpServletRequest request) {
    this.requestRateLimiter.checkLimit(
        "auth-login-ip",
        resolveClientIp(request),
        12,
        300,
        "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
    this.requestRateLimiter.checkLimit(
        "auth-login-email",
        authDTO.email(),
        8,
        300,
        "Muitas tentativas para este usuario. Aguarde alguns minutos e tente novamente.");
    var response = this.authUseCase.execute(authDTO);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/switch-sector")
  public ResponseEntity<TokenResponseDTO> switchSector(
      @Valid @RequestBody SwitchSetorDTO payload,
      HttpServletRequest request,
      org.springframework.security.core.Authentication authentication) {
    var actor = (UsuarioEntity) authentication.getPrincipal();
    this.requestRateLimiter.checkLimit(
        "auth-switch-sector-ip",
        resolveClientIp(request),
        30,
        300,
        "Muitas trocas de setor em pouco tempo. Aguarde alguns instantes e tente novamente.");
    return ResponseEntity.ok(this.authUseCase.switchSetor(actor, payload.setor()));
  }

  @GetMapping("/setores")
  public ResponseEntity<Iterable<SetorOptionDTO>> listSetores() {
    return ResponseEntity.ok(this.serviceUseCase.getLoginSetores());
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<MessageResponseDTO> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequestDTO request,
      HttpServletRequest servletRequest) {
    this.requestRateLimiter.checkLimit(
        "forgot-password-ip",
        resolveClientIp(servletRequest),
        6,
        600,
        "Muitas solicitacoes de recuperacao. Aguarde alguns minutos para tentar novamente.");
    this.requestRateLimiter.checkLimit(
        "forgot-password-email",
        request.email(),
        3,
        600,
        "Muitas solicitacoes para este email. Aguarde alguns minutos para tentar novamente.");
    return ResponseEntity.ok(this.passwordResetUseCase.requestReset(request.email()));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<MessageResponseDTO> resetPassword(
      @Valid @RequestBody ResetPasswordRequestDTO request,
      HttpServletRequest servletRequest) {
    this.requestRateLimiter.checkLimit(
        "reset-password-token-ip",
        resolveClientIp(servletRequest),
        8,
        600,
        "Muitas tentativas de redefinicao. Aguarde alguns minutos para tentar novamente.");
    return ResponseEntity.ok(this.passwordResetUseCase.resetPassword(request.token(), request.newPassword()));
  }

  @PostMapping("/reset-password/recovery-code")
  public ResponseEntity<MessageResponseDTO> resetPasswordWithRecoveryCode(
      @Valid @RequestBody RecoveryCodeResetRequestDTO request,
      HttpServletRequest servletRequest) {
    this.requestRateLimiter.checkLimit(
        "reset-password-recovery-ip",
        resolveClientIp(servletRequest),
        8,
        600,
        "Muitas tentativas de redefinicao. Aguarde alguns minutos para tentar novamente.");
    this.requestRateLimiter.checkLimit(
        "reset-password-recovery-email",
        request.email(),
        5,
        600,
        "Muitas tentativas para este usuario. Aguarde alguns minutos para tentar novamente.");
    return ResponseEntity.ok(
        this.passwordResetUseCase.resetPasswordWithRecoveryCode(
            request.email(),
            request.recoveryCode(),
            request.newPassword()));
  }

  private String resolveClientIp(HttpServletRequest request) {
    var forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    var realIp = request.getHeader("X-Real-IP");
    if (realIp != null && !realIp.isBlank()) {
      return realIp.trim();
    }
    return String.valueOf(request.getRemoteAddr());
  }
}
