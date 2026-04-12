package com.wlilan.backend_assistent.usuario.useCases.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wlilan.backend_assistent.DTO.MessageResponseDTO;
import com.wlilan.backend_assistent.exeptions.InvalidCredentialsException;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@Service
public class PasswordResetUseCase {

  private static final String GENERIC_RESPONSE_MESSAGE =
      "Se o email estiver cadastrado, enviaremos um link de recuperacao.";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final UsuarioRepository usuarioRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetMailService passwordResetMailService;
  private final long tokenTtlMinutes;

  public PasswordResetUseCase(
      UsuarioRepository usuarioRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordEncoder passwordEncoder,
      PasswordResetMailService passwordResetMailService,
      @Value("${app.password-reset.token-ttl-minutes:30}") long tokenTtlMinutes) {
    this.usuarioRepository = usuarioRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordResetMailService = passwordResetMailService;
    this.tokenTtlMinutes = tokenTtlMinutes;
  }

  @Transactional
  public MessageResponseDTO requestReset(String email) {
    this.passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

    var usuarioOpt = this.usuarioRepository.findByEmail(normalizeEmail(email));
    if (usuarioOpt.isEmpty()) {
      return new MessageResponseDTO(GENERIC_RESPONSE_MESSAGE);
    }

    var usuario = usuarioOpt.get();
    invalidatePreviousActiveTokens(usuario.getId());

    var rawToken = generateRawToken();
    var tokenEntity = new PasswordResetTokenEntity();
    tokenEntity.setUsuario(usuario);
    tokenEntity.setTokenHash(hashToken(rawToken));
    tokenEntity.setCreatedAt(LocalDateTime.now());
    tokenEntity.setExpiresAt(LocalDateTime.now().plusMinutes(this.tokenTtlMinutes));
    this.passwordResetTokenRepository.save(tokenEntity);

    this.passwordResetMailService.sendResetLink(usuario, rawToken);
    return new MessageResponseDTO(GENERIC_RESPONSE_MESSAGE);
  }

  @Transactional
  public MessageResponseDTO resetPassword(String token, String newPassword) {
    var hashedToken = hashToken(token);
    var resetToken = this.passwordResetTokenRepository.findByTokenHash(hashedToken)
        .orElseThrow(() -> new InvalidCredentialsException("Token de recuperacao invalido ou expirado."));

    var now = LocalDateTime.now();
    if (!resetToken.isActiveAt(now)) {
      throw new InvalidCredentialsException("Token de recuperacao invalido ou expirado.");
    }

    var usuario = resetToken.getUsuario();
    usuario.setPassword(this.passwordEncoder.encode(newPassword));
    this.usuarioRepository.save(usuario);

    resetToken.setUsedAt(now);
    this.passwordResetTokenRepository.save(resetToken);
    invalidatePreviousActiveTokens(usuario.getId());

    return new MessageResponseDTO("Senha redefinida com sucesso.");
  }

  @Transactional
  public MessageResponseDTO resetPasswordWithRecoveryCode(String email, String recoveryCode, String newPassword) {
    var usuario = this.usuarioRepository.findByEmail(normalizeEmail(email))
        .orElseThrow(() -> new InvalidCredentialsException("Codigo de recuperacao invalido."));

    var recoveryCodeHash = usuario.getRecoveryCodeHash();
    if (recoveryCodeHash == null
        || recoveryCodeHash.isBlank()
        || !this.passwordEncoder.matches(String.valueOf(recoveryCode).trim(), recoveryCodeHash)) {
      throw new InvalidCredentialsException("Codigo de recuperacao invalido.");
    }

    usuario.setPassword(this.passwordEncoder.encode(newPassword));
    this.usuarioRepository.save(usuario);
    invalidatePreviousActiveTokens(usuario.getId());
    return new MessageResponseDTO("Senha redefinida com sucesso.");
  }

  private void invalidatePreviousActiveTokens(java.util.UUID usuarioId) {
    var now = LocalDateTime.now();
    var activeTokens = this.passwordResetTokenRepository.findAllByUsuario_IdAndUsedAtIsNull(usuarioId);
    for (var activeToken : activeTokens) {
      if (activeToken.isActiveAt(now)) {
        activeToken.setUsedAt(now);
      }
    }
    this.passwordResetTokenRepository.saveAll(activeTokens);
  }

  private String generateRawToken() {
    var bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var encoded = digest.digest(String.valueOf(rawToken).trim().getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder(encoded.length * 2);
      for (var value : encoded) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao preparar token de recuperacao.", exception);
    }
  }

  private String normalizeEmail(String email) {
    return String.valueOf(email == null ? "" : email).trim().toLowerCase();
  }
}
