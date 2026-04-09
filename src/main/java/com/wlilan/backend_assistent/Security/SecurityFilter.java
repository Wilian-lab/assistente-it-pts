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

    log.info("SecurityFilter request method={} uri={}", request.getMethod(), request.getRequestURI());
    var token = recoverToken(request);
    if (token != null) {
      log.info("Authorization header found. Token prefix={}...", token.substring(0, Math.min(12, token.length())));
      var subject = this.tokenService.validateToken(token);

      if (subject != null) {
        log.info("JWT validated. Subject={}", subject);
        var usuarioOpt = this.usuarioRepository.findByEmail(subject);
        if (usuarioOpt.isPresent()) {
          var usuario = usuarioOpt.get();
              var role = usuario.getRole() != null ? usuario.getRole() : Role.USER;
              log.info("User found for subject={}. role={}", subject, role);
              var authorities = List.of(
                  new SimpleGrantedAuthority("ROLE_" + role.name()));
              var authentication = new UsernamePasswordAuthenticationToken(
                  usuario,
                  null,
                  authorities);

              SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
          log.warn("JWT validated but no user found with email={}", subject);
        }
      } else {
        log.warn("JWT validation returned null subject for request {}", request.getRequestURI());
      }
    } else {
      log.warn("No Bearer token found for request {}", request.getRequestURI());
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
