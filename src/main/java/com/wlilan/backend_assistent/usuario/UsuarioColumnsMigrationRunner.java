package com.wlilan.backend_assistent.usuario;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 90)
public class UsuarioColumnsMigrationRunner implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;

  public UsuarioColumnsMigrationRunner(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    if (!usuarioTableExists()) {
      return;
    }

    this.jdbcTemplate.execute("ALTER TABLE usuario ADD COLUMN IF NOT EXISTS training_status VARCHAR(255)");
    this.jdbcTemplate.execute("ALTER TABLE usuario ADD COLUMN IF NOT EXISTS profile_image_content_type VARCHAR(255)");
    ensureProfileImageDataColumn();
  }

  private void ensureProfileImageDataColumn() {
    var currentType = this.jdbcTemplate.query(
        """
            SELECT c.data_type, c.udt_name
            FROM information_schema.columns c
            WHERE c.table_schema = current_schema()
              AND c.table_name = 'usuario'
              AND c.column_name = 'profile_image_data'
            """,
        rs -> {
          if (!rs.next()) {
            return null;
          }
          var dataType = String.valueOf(rs.getString("data_type"));
          var udtName = String.valueOf(rs.getString("udt_name"));
          return (dataType + ":" + udtName).toLowerCase();
        });

    if (currentType == null) {
      this.jdbcTemplate.execute("ALTER TABLE usuario ADD COLUMN profile_image_data BYTEA");
      return;
    }

    if (currentType.contains("bytea")) {
      return;
    }

    if (currentType.contains(":oid")) {
      this.jdbcTemplate.execute("ALTER TABLE usuario ADD COLUMN IF NOT EXISTS profile_image_data_tmp BYTEA");
      this.jdbcTemplate.execute("""
          UPDATE usuario
          SET profile_image_data_tmp = CASE
            WHEN profile_image_data IS NULL THEN NULL
            ELSE lo_get(profile_image_data)
          END
          """);
      this.jdbcTemplate.execute("ALTER TABLE usuario DROP COLUMN profile_image_data");
      this.jdbcTemplate.execute("ALTER TABLE usuario RENAME COLUMN profile_image_data_tmp TO profile_image_data");
      return;
    }

    this.jdbcTemplate.execute("ALTER TABLE usuario DROP COLUMN profile_image_data");
    this.jdbcTemplate.execute("ALTER TABLE usuario ADD COLUMN profile_image_data BYTEA");
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
