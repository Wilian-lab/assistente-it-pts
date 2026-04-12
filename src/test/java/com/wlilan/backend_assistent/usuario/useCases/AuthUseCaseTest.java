package com.wlilan.backend_assistent.usuario.useCases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wlilan.backend_assistent.DTO.AuthDTO;
import com.wlilan.backend_assistent.Security.TokenService;
import com.wlilan.backend_assistent.exeptions.InvalidCredentialsException;
import com.wlilan.backend_assistent.exeptions.InvalidSetorAccessException;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseTest {

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private TokenService tokenService;

  private AuthUseCase authUseCase;

  @BeforeEach
  void setUp() {
    this.authUseCase = new AuthUseCase(this.usuarioRepository, this.passwordEncoder, this.tokenService);
  }

  @Test
  void shouldReturnForbiddenMessageWhenSetorDoesNotBelongToUser() {
    var usuario = new UsuarioEntity();
    usuario.setEmail("user@teste.com");
    usuario.setPassword("hash");
    usuario.setRole(Role.USER);
    usuario.setSetores("MOAGEM");

    when(this.usuarioRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(usuario));
    when(this.passwordEncoder.matches("senha123", "hash")).thenReturn(true);

    assertThrows(InvalidSetorAccessException.class,
        () -> this.authUseCase.execute(new AuthDTO("user@teste.com", "AGRI_PRODUCTS", "senha123")));
  }

  @Test
  void shouldKeepInvalidCredentialsForWrongPassword() {
    var usuario = new UsuarioEntity();
    usuario.setEmail("user@teste.com");
    usuario.setPassword("hash");
    usuario.setSetores("MOAGEM");

    when(this.usuarioRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(usuario));
    when(this.passwordEncoder.matches("senha123", "hash")).thenReturn(false);

    assertThrows(InvalidCredentialsException.class,
        () -> this.authUseCase.execute(new AuthDTO("user@teste.com", "MOAGEM", "senha123")));
  }

  @Test
  void shouldGenerateTokenWhenCredentialsAndSetorAreValid() {
    var usuario = new UsuarioEntity();
    usuario.setEmail("user@teste.com");
    usuario.setPassword("hash");
    usuario.setRole(Role.USER);
    usuario.setSetores("MOAGEM");

    when(this.usuarioRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(usuario));
    when(this.passwordEncoder.matches("senha123", "hash")).thenReturn(true);
    when(this.tokenService.generateToken(anyString(), anyString(), anyString())).thenReturn("jwt-token");
    when(this.tokenService.getExpiresInSeconds()).thenReturn(28800L);

    var response = this.authUseCase.execute(new AuthDTO("user@teste.com", "MOAGEM", "senha123"));

    assertEquals("jwt-token", response.accessToken());
    assertEquals(28800L, response.expiresIn());
    assertEquals("MOAGEM", response.user().getSetorAtivo());
  }

  @Test
  void shouldAllowSuperAdminToLoginInAnySector() {
    var usuario = new UsuarioEntity();
    usuario.setEmail("master@teste.com");
    usuario.setPassword("hash");
    usuario.setRole(Role.SUPER_ADMIN);
    usuario.setSetores("MOAGEM");

    when(this.usuarioRepository.findByEmail("master@teste.com")).thenReturn(Optional.of(usuario));
    when(this.passwordEncoder.matches("senha123", "hash")).thenReturn(true);
    when(this.tokenService.generateToken(anyString(), anyString(), anyString())).thenReturn("jwt-master");
    when(this.tokenService.getExpiresInSeconds()).thenReturn(28800L);

    var response = this.authUseCase.execute(new AuthDTO("master@teste.com", "EDIFICIO_27", "senha123"));

    assertEquals("jwt-master", response.accessToken());
    assertEquals("EDIFICIO_27", response.user().getSetorAtivo());
  }
}
