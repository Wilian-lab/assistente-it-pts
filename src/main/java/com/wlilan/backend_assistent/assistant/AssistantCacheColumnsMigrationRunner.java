package com.wlilan.backend_assistent.assistant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AssistantCacheColumnsMigrationRunner implements CommandLineRunner {

  private final JdbcTemplate jdbcTemplate;

  public AssistantCacheColumnsMigrationRunner(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    if (!tableExists("assistant_cache")) {
      return;
    }

    addColumnIfMissing("assistant_cache", "original_source_type", "varchar(120)");
    addColumnIfMissing("assistant_cache", "original_provider", "varchar(80)");

    this.jdbcTemplate.execute("""
        update assistant_cache
        set original_source_type = coalesce(nullif(original_source_type, ''), 'unknown')
        where original_source_type is null
        """);

    this.jdbcTemplate.execute("""
        update assistant_cache
        set original_provider = coalesce(nullif(original_provider, ''), 'unknown')
        where original_provider is null
        """);
  }

  private boolean tableExists(String tableName) {
    Integer count = this.jdbcTemplate.queryForObject("""
        select count(*)
        from information_schema.tables
        where table_schema = 'public' and table_name = ?
        """, Integer.class, tableName);
    return count != null && count > 0;
  }

  private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
    Integer count = this.jdbcTemplate.queryForObject("""
        select count(*)
        from information_schema.columns
        where table_schema = 'public' and table_name = ? and column_name = ?
        """, Integer.class, tableName, columnName);

    if (count != null && count > 0) {
      return;
    }

    this.jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
  }
}
