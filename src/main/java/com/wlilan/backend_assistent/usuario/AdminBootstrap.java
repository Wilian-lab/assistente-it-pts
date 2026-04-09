package com.wlilan.backend_assistent.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {

  private static final long MAX_ADMINS = 5;

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.admin.name:}")
  private String adminName;

  @Value("${app.admin.email:}")
  private String adminEmail;

  @Value("${app.admin.password:}")
  private String adminPassword;

  public AdminBootstrap(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
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
        this.usuarioRepository.save(admin);
      }
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

    this.usuarioRepository.save(admin);
  }

  private boolean canCreateOrPromoteAdmin() {
    return this.usuarioRepository.countByRole(Role.ADMIN) < MAX_ADMINS;
  }

  private static boolean isValidEmail(String email) {
    // Keep this strict enough to catch obvious misconfigurations without adding dependencies.
    return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  }
}
