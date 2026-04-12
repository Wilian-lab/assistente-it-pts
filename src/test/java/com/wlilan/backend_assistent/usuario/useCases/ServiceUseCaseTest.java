package com.wlilan.backend_assistent.usuario.useCases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wlilan.backend_assistent.DTO.AdminCreateUserDTO;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.SetorEntity;
import com.wlilan.backend_assistent.usuario.SetorRepository;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;
import com.wlilan.backend_assistent.usuario.useCases.passwordreset.PasswordResetMailService;

@ExtendWith(MockitoExtension.class)
class ServiceUseCaseTest {

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private SetorRepository setorRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private PasswordResetMailService passwordResetMailService;

  private ServiceUseCase serviceUseCase;

  @BeforeEach
  void setUp() {
    this.serviceUseCase = new ServiceUseCase(
        this.usuarioRepository,
        this.setorRepository,
        this.passwordEncoder,
        this.passwordResetMailService,
        "MOAGEM,AGRI_PRODUCTS");
  }

  @Test
  void shouldBlockAdminCreationOutsideAuthenticatedSector() {
    var dto = new AdminCreateUserDTO();
    dto.setName("Teste Usuario");
    dto.setEmail("teste@empresa.com");
    dto.setPassword("senha123");
    dto.setRole("USER");
    dto.setCargo("OPERADOR");
    dto.setSetores("AGRI_PRODUCTS");

    var actor = new UsuarioEntity();
    actor.setRole(Role.ADMIN);
    actor.setSetorAtivo("MOAGEM");

    when(this.usuarioRepository.findByNameOrEmail(dto.getName(), dto.getEmail())).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> this.serviceUseCase.executeAdminCreate(dto, actor));
    verify(this.usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any(UsuarioEntity.class));
  }

  @Test
  void shouldCreateUserInsideAuthenticatedSector() {
    var dto = new AdminCreateUserDTO();
    dto.setName("Teste Usuario");
    dto.setEmail("teste@empresa.com");
    dto.setPassword("senha123");
    dto.setRole("USER");
    dto.setCargo("OPERADOR");
    dto.setSetores("MOAGEM");

    var actor = new UsuarioEntity();
    actor.setRole(Role.ADMIN);
    actor.setSetorAtivo("MOAGEM");

    var setor = new SetorEntity();
    setor.setCodigo("MOAGEM");

    when(this.usuarioRepository.findByNameOrEmail(dto.getName(), dto.getEmail())).thenReturn(Optional.empty());
    when(this.setorRepository.findByCodigo("MOAGEM")).thenReturn(Optional.of(setor));
    when(this.usuarioRepository.save(org.mockito.ArgumentMatchers.any(UsuarioEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(this.passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash");
    doNothing().when(this.passwordResetMailService)
        .sendRecoveryCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    var created = this.serviceUseCase.executeAdminCreate(dto, actor);

    assertEquals("hash", created.user().getPassword());
    assertEquals("MOAGEM", created.user().getSetor());
    assertNotNull(created.recoveryCode());
    assertEquals(true, created.emailSent());
  }

  @Test
  void shouldAllowAdminToCreateUserInAnyAssignedSector() {
    var dto = new AdminCreateUserDTO();
    dto.setName("Treinando Operador");
    dto.setEmail("operador@empresa.com");
    dto.setPassword("senha123");
    dto.setRole("USER");
    dto.setCargo("OPERADOR");
    dto.setSetores("AGRI_PRODUCTS");

    var actor = new UsuarioEntity();
    actor.setRole(Role.ADMIN);
    actor.setSetorAtivo("MOAGEM");

    var moagem = new SetorEntity();
    moagem.setCodigo("MOAGEM");
    var agri = new SetorEntity();
    agri.setCodigo("AGRI_PRODUCTS");
    actor.setSetoresRelacionados(Set.of(moagem, agri));

    when(this.usuarioRepository.findByNameOrEmail(dto.getName(), dto.getEmail())).thenReturn(Optional.empty());
    when(this.setorRepository.findByCodigo("AGRI_PRODUCTS")).thenReturn(Optional.of(agri));
    when(this.usuarioRepository.save(org.mockito.ArgumentMatchers.any(UsuarioEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(this.passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hash");
    doNothing().when(this.passwordResetMailService)
        .sendRecoveryCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean());

    var created = this.serviceUseCase.executeAdminCreate(dto, actor);

    assertEquals("AGRI_PRODUCTS", created.user().getSetor());
  }
}
