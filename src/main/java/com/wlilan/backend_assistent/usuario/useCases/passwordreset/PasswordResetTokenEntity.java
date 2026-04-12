package com.wlilan.backend_assistent.usuario.useCases.passwordreset;

import java.time.LocalDateTime;
import java.util.UUID;

import com.wlilan.backend_assistent.usuario.UsuarioEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "password_reset_token")
public class PasswordResetTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "usuario_id", nullable = false)
  private UsuarioEntity usuario;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  public boolean isActiveAt(LocalDateTime now) {
    return this.usedAt == null && this.expiresAt != null && this.expiresAt.isAfter(now);
  }
}
