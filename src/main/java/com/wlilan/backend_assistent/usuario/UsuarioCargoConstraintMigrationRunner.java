package com.wlilan.backend_assistent.usuario;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 110)
public class UsuarioCargoConstraintMigrationRunner implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;

  public UsuarioCargoConstraintMigrationRunner(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    if (!usuarioTableExists()) {
      return;
    }

    this.jdbcTemplate.execute("ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_cargo_check");
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
