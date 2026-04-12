package com.wlilan.backend_assistent.usuario.passwordreset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wlilan.backend_assistent.DTO.MessageResponseDTO;
import com.wlilan.backend_assistent.exeptions.InvalidCredentialsException;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;
import com.wlilan.backend_assistent.usuario.useCases.passwordreset.PasswordResetMailService;
import com.wlilan.backend_assistent.usuario.useCases.passwordreset.PasswordResetTokenRepository;
import com.wlilan.backend_assistent.usuario.useCases.passwordreset.PasswordResetUseCase;

@ExtendWith(MockitoExtension.class)
class PasswordResetUseCaseTest {

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private PasswordResetTokenRepository passwordResetTokenRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private PasswordResetMailService passwordResetMailService;

  private PasswordResetUseCase passwordResetUseCase;

  @BeforeEach
  void setUp() {
    this.passwordResetUseCase = new PasswordResetUseCase(
        this.usuarioRepository,
        this.passwordResetTokenRepository,
        this.passwordEncoder,
        this.passwordResetMailService,
        30);
  }

  @Test
  void shouldResetPasswordWithValidRecoveryCode() {
    var usuario = new UsuarioEntity();
    usuario.setEmail("admin@teste.com");
    usuario.setRecoveryCodeHash("hash-code");

    when(this.usuarioRepository.findByEmail("admin@teste.com")).thenReturn(Optional.of(usuario));
    when(this.passwordEncoder.matches("COD-123", "hash-code")).thenReturn(true);
    when(this.passwordEncoder.encode("NovaSenha123")).thenReturn("hash-password");

    MessageResponseDTO response =
        this.passwordResetUseCase.resetPasswordWithRecoveryCode("admin@teste.com", "COD-123", "NovaSenha123");

    assertEquals("Senha redefinida com sucesso.", response.message());
    assertEquals("hash-password", usuario.getPassword());
    verify(this.usuarioRepository).save(usuario);
  }

  @Test
  void shouldRejectInvalidRecoveryCode() {
    var usuario = new UsuarioEntity();
    usuario.setEmail("admin@teste.com");
    usuario.setRecoveryCodeHash("hash-code");

    when(this.usuarioRepository.findByEmail("admin@teste.com")).thenReturn(Optional.of(usuario));
    when(this.passwordEncoder.matches("COD-ERRADO", "hash-code")).thenReturn(false);

    assertThrows(
        InvalidCredentialsException.class,
        () -> this.passwordResetUseCase.resetPasswordWithRecoveryCode("admin@teste.com", "COD-ERRADO", "NovaSenha123"));
  }
}
