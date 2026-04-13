package com.wlilan.backend_assistent.usuario.useCases;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.DTO.AuthDTO;
import com.wlilan.backend_assistent.DTO.TokenResponseDTO;
import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.exeptions.InvalidCredentialsException;
import com.wlilan.backend_assistent.exeptions.InvalidSetorAccessException;
import com.wlilan.backend_assistent.Security.TokenService;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@Service
public class AuthUseCase {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final ServiceUseCase serviceUseCase;

  public AuthUseCase(
      UsuarioRepository usuarioRepository,
      PasswordEncoder passwordEncoder,
      TokenService tokenService,
      ServiceUseCase serviceUseCase) {
    this.usuarioRepository = usuarioRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.serviceUseCase = serviceUseCase;
  }

  public TokenResponseDTO execute(AuthDTO authDTO) {
    var usuario = this.usuarioRepository.findByEmail(authDTO.email())
        .orElseThrow(InvalidCredentialsException::new);

    var passwordMatches = this.passwordEncoder.matches(authDTO.password(), usuario.getPassword());
    if (!passwordMatches) {
      throw new InvalidCredentialsException();
    }

    var setorAtivo = SetorSupport.normalize(authDTO.setor());
    var role = usuario.getRole() != null ? usuario.getRole() : Role.USER;
    if (role != Role.SUPER_ADMIN && !SetorSupport.userHasSetor(usuario.getSetores(), setorAtivo)) {
      throw new InvalidSetorAccessException();
    }

    usuario.setSetorAtivo(setorAtivo);
    var token = this.tokenService.generateToken(usuario.getEmail(), role.name(), setorAtivo);
    return new TokenResponseDTO(token, this.tokenService.getExpiresInSeconds(), usuario);
  }

  public TokenResponseDTO switchSetor(UsuarioEntity actor, String setorSolicitado) {
    var usuario = this.usuarioRepository.findByEmail(actor.getEmail())
        .orElseThrow(InvalidCredentialsException::new);

    var setorAtivo = SetorSupport.normalize(setorSolicitado);
    if (!this.serviceUseCase.actorCanAccessSetor(usuario, setorAtivo)) {
      throw new InvalidSetorAccessException();
    }

    var role = usuario.getRole() != null ? usuario.getRole() : Role.USER;
    usuario.setSetorAtivo(setorAtivo);
    var token = this.tokenService.generateToken(usuario.getEmail(), role.name(), setorAtivo);
    return new TokenResponseDTO(token, this.tokenService.getExpiresInSeconds(), usuario);
  }
}
