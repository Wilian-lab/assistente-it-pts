package com.wlilan.backend_assistent.usuario;

import com.wlilan.backend_assistent.Security.SetorSupport;
import java.util.LinkedHashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminBootstrap implements CommandLineRunner {

  private static final long MAX_ADMINS = 5;

  private final UsuarioRepository usuarioRepository;
  private final SetorRepository setorRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.admin.name:}")
  private String adminName;

  @Value("${app.admin.email:}")
  private String adminEmail;

  @Value("${app.admin.password:}")
  private String adminPassword;

  @Value("${app.admin.setores:AGRI_PRODUCTS,MOAGEM}")
  private String adminSetores;

  public AdminBootstrap(UsuarioRepository usuarioRepository, SetorRepository setorRepository, PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
    this.setorRepository = setorRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    adminName = adminName.strip();
    adminEmail = adminEmail.strip();
    adminPassword = adminPassword.strip();

    if (adminName.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
      return;
    }

    if (!isValidEmail(adminEmail)) {
      throw new IllegalStateException(
          "Invalid configuration for admin bootstrap: property 'app.admin.email' must be a valid email, got: '"
              + adminEmail
              + "'");
    }

    var existingAdmin = this.usuarioRepository.findByEmail(adminEmail);
    if (existingAdmin.isPresent()) {
      var admin = existingAdmin.get();
      if (admin.getRole() != Role.ADMIN && canCreateOrPromoteAdmin()) {
        admin.setRole(Role.ADMIN);
      }
      admin.setSetores(String.join(",", SetorSupport.parseSetores(adminSetores)));
      admin.setSetoresRelacionados(resolveSetores(admin.getSetores()));
      this.usuarioRepository.save(admin);
      return;
    }

    if (!canCreateOrPromoteAdmin()) {
      return;
    }

    var admin = new UsuarioEntity();
    admin.setName(adminName);
    admin.setEmail(adminEmail);
    admin.setPassword(this.passwordEncoder.encode(adminPassword));
    admin.setRole(Role.ADMIN);
    admin.setSetores(String.join(",", SetorSupport.parseSetores(adminSetores)));
    admin.setSetoresRelacionados(resolveSetores(admin.getSetores()));

    this.usuarioRepository.save(admin);
  }

  private boolean canCreateOrPromoteAdmin() {
    return this.usuarioRepository.countByRole(Role.ADMIN) < MAX_ADMINS;
  }

  private static boolean isValidEmail(String email) {
    // Keep this strict enough to catch obvious misconfigurations without adding dependencies.
    return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  }

  private java.util.List<SetorEntity> resolveSetores(String rawSetores) {
    return new LinkedHashSet<>(SetorSupport.parseSetores(rawSetores)).stream()
        .map(this::findOrCreateSetor)
        .toList();
  }

  private SetorEntity findOrCreateSetor(String codigo) {
    var normalizedCodigo = SetorSupport.normalize(codigo);
    return this.setorRepository.findByCodigo(normalizedCodigo)
        .orElseGet(() -> {
          var setor = new SetorEntity();
          setor.setCodigo(normalizedCodigo);
          return this.setorRepository.save(setor);
        });
  }
}
