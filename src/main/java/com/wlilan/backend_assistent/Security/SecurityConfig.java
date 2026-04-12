package com.wlilan.backend_assistent.Security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;
import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  private final SecurityFilter securityFilter;
  private final List<String> allowedOriginPatterns;

  public SecurityConfig(
      SecurityFilter securityFilter,
      @Value("${app.security.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String allowedOriginPatternsRaw) {
    this.securityFilter = securityFilter;
    this.allowedOriginPatterns = Arrays.stream(String.valueOf(allowedOriginPatternsRaw == null ? "" : allowedOriginPatternsRaw).split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint((request, response, authException) -> {
              var authErrorCode = String.valueOf(request.getAttribute(SecurityFilter.AUTH_ERROR_CODE_ATTRIBUTE));
              var message = SecurityFilter.AUTH_ERROR_TOKEN_EXPIRED.equals(authErrorCode)
                  ? "Sua sessao expirou. Faca login novamente para continuar."
                  : "Voce precisa estar autenticado para acessar este recurso";

              response.setStatus(HttpStatus.UNAUTHORIZED.value());
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.setCharacterEncoding("UTF-8");

              var body = String.format(
                  "{\"message\":\"%s\",\"field\":null,\"status\":%d,\"timestamp\":\"%s\"}",
                  message,
                  HttpStatus.UNAUTHORIZED.value(),
                  LocalDateTime.now());

              response.getWriter().write(body);
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
              response.setStatus(HttpStatus.FORBIDDEN.value());
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.setCharacterEncoding("UTF-8");

              var body = String.format(
                  "{\"message\":\"%s\",\"field\":null,\"status\":%d,\"timestamp\":\"%s\"}",
                  "Somente administrador pode realizar esta operacao",
                  HttpStatus.FORBIDDEN.value(),
                  LocalDateTime.now());

              response.getWriter().write(body);
            }))
        .authorizeHttpRequests(authorize -> authorize
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers(HttpMethod.GET, "/auth/setores").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth/forgot-password").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth/reset-password").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth/reset-password/recovery-code").permitAll()
            .requestMatchers(HttpMethod.GET, "/it/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
            .requestMatchers(HttpMethod.POST, "/it/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.PUT, "/it/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/it/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .requestMatchers(HttpMethod.GET, "/usuario/me").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
            .requestMatchers(HttpMethod.GET, "/usuario/me/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
            .requestMatchers(HttpMethod.PUT, "/usuario/me/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
            .requestMatchers(HttpMethod.POST, "/usuario/me/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
            .requestMatchers(HttpMethod.DELETE, "/usuario/me/**").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER")
            .requestMatchers(HttpMethod.GET, "/usuario/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
            .anyRequest().authenticated())
        .addFilterBefore(this.securityFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(this.allowedOriginPatterns.isEmpty()
        ? List.of("http://localhost:*", "http://127.0.0.1:*")
        : this.allowedOriginPatterns);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
