package com.wlilan.backend_assistent.Security;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.servlet.DispatcherType;

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

  public SecurityConfig(SecurityFilter securityFilter) {
    this.securityFilter = securityFilter;
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
            .requestMatchers(HttpMethod.POST, "/usuario").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
            .requestMatchers(HttpMethod.GET, "/it/**").hasAnyRole("ADMIN", "USER")
            .requestMatchers(HttpMethod.POST, "/it/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/it/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/it/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin/users/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/admin/users/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/admin/users/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/admin/users/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/usuario/me").hasAnyRole("ADMIN", "USER")
            .requestMatchers(HttpMethod.GET, "/usuario/**").hasRole("ADMIN")
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
    configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
