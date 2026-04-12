package com.wlilan.backend_assistent.assistant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 120)
public class AssistantDocumentBlockMigrationRunner implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;

  public AssistantDocumentBlockMigrationRunner(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    if (!tableExists("assistant_document_block")) {
      return;
    }

    this.jdbcTemplate.execute("ALTER TABLE assistant_document_block ADD COLUMN IF NOT EXISTS setor VARCHAR(120)");
    this.jdbcTemplate.execute("""
        UPDATE assistant_document_block adb
        SET setor = upper(trim(it.setor))
        FROM it
        WHERE adb.it_id = it.id
          AND (adb.setor IS NULL OR trim(adb.setor) = '')
        """);
    this.jdbcTemplate.execute("UPDATE assistant_document_block SET setor = 'GLOBAL' WHERE setor IS NULL OR trim(setor) = ''");
    this.jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_assistant_document_block_setor ON assistant_document_block(setor)");
    this.jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_assistant_document_block_setor_documento ON assistant_document_block(setor, documento)");
  }

  private boolean tableExists(String tableName) {
    var result = this.jdbcTemplate.queryForObject(
        """
            SELECT EXISTS (
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = current_schema()
                AND table_name = ?
            )
            """,
        Boolean.class,
        tableName);
    return Boolean.TRUE.equals(result);
  }
}
