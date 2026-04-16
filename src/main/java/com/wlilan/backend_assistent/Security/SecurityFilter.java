package com.wlilan.backend_assistent.Security;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.wlilan.backend_assistent.usuario.Role;
import com.wlilan.backend_assistent.usuario.UsuarioRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityFilter extends OncePerRequestFilter {

  public static final String AUTH_ERROR_CODE_ATTRIBUTE = "auth_error_code";
  public static final String AUTH_ERROR_TOKEN_EXPIRED = "token_expired";

  private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

  private final TokenService tokenService;
  private final UsuarioRepository usuarioRepository;

  public SecurityFilter(TokenService tokenService, UsuarioRepository usuarioRepository) {
    this.tokenService = tokenService;
    this.usuarioRepository = usuarioRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    var token = recoverToken(request);
    if (token != null) {
      var validation = this.tokenService.validate(token);

      if (validation.isValid()) {
        var setorAtivo = SetorSupport.normalize(validation.setorAtivo());
        var usuarioOpt = this.usuarioRepository.findByEmail(validation.subject());
        if (usuarioOpt.isPresent()) {
          var usuario = usuarioOpt.get();
          usuario.setSetorAtivo(setorAtivo);
          var role = usuario.getRole() != null ? usuario.getRole() : Role.USER;
          var authorities = List.of(
              new SimpleGrantedAuthority("ROLE_" + role.name()));
          var authentication = new UsernamePasswordAuthenticationToken(
              usuario,
              null,
              authorities);

          SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
          log.warn("JWT validado, mas nenhum usuario foi encontrado para o subject informado.");
        }
      } else {
        if (validation.isExpired()) {
          request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, AUTH_ERROR_TOKEN_EXPIRED);
        }
        log.warn("JWT validation returned null subject for request {}", request.getRequestURI());
      }
    }

    filterChain.doFilter(request, response);
  }

  private String recoverToken(HttpServletRequest request) {
    var authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }

    return authHeader.replace("Bearer ", "");
  }
}
