package com.wlilan.backend_assistent.usuario.useCases;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.DTO.AuthDTO;
import com.wlilan.backend_assistent.DTO.TokenResponseDTO;
import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.exeptions.InvalidCredentialsException;
import com.wlilan.backend_assistent.Security.TokenService;
import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

@Service
public class AuthUseCase {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

  public AuthUseCase(
      UsuarioRepository usuarioRepository,
      PasswordEncoder passwordEncoder,
      TokenService tokenService) {
    this.usuarioRepository = usuarioRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
  }

  public TokenResponseDTO execute(AuthDTO authDTO) {
    var usuario = this.usuarioRepository.findByEmail(authDTO.email())
        .orElseThrow(InvalidCredentialsException::new);

    var passwordMatches = this.passwordEncoder.matches(authDTO.password(), usuario.getPassword());
    if (!passwordMatches) {
      throw new InvalidCredentialsException();
    }

    var setorAtivo = SetorSupport.normalize(authDTO.setor());
    if (!SetorSupport.userHasSetor(usuario.getSetores(), setorAtivo)) {
      throw new InvalidCredentialsException();
    }

    var role = usuario.getRole() != null ? usuario.getRole() : Role.USER;
    usuario.setSetorAtivo(setorAtivo);
    var token = this.tokenService.generateToken(usuario.getEmail(), role.name(), setorAtivo);
    return new TokenResponseDTO(token, this.tokenService.getExpiresInSeconds(), usuario);
  }
}
