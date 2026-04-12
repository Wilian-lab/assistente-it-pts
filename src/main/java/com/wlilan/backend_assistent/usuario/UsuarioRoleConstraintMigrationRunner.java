package com.wlilan.backend_assistent.usuario;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class UsuarioRoleConstraintMigrationRunner implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;

  public UsuarioRoleConstraintMigrationRunner(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    if (!usuarioTableExists()) {
      return;
    }

    this.jdbcTemplate.execute("ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_role_check");
    this.jdbcTemplate.execute("""
        ALTER TABLE usuario
        ADD CONSTRAINT usuario_role_check
        CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER'))
        """);
  }

  private boolean usuarioTableExists() {
    var result = this.jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = current_schema()
                AND table_name = 'usuario'
            )
            """,
        Boolean.class);
    return Boolean.TRUE.equals(result);
  }
}
