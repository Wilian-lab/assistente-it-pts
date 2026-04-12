package com.wlilan.backend_assistent.usuario.useCases.passwordreset;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

  List<PasswordResetTokenEntity> findAllByUsuario_IdAndUsedAtIsNull(UUID usuarioId);

  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  void deleteByExpiresAtBefore(LocalDateTime threshold);
}
