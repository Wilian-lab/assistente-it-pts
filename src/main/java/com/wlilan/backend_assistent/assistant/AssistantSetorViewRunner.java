package com.wlilan.backend_assistent.assistant;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.wlilan.backend_assistent.Security.SetorSupport;
import com.wlilan.backend_assistent.usuario.SetorRepository;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class AssistantSetorViewRunner implements CommandLineRunner {

  private static final String DOCUMENT_VIEW_PREFIX = "vw_assistant_document_block_";
  private static final String CACHE_VIEW_PREFIX = "vw_assistant_cache_";

  private final SetorRepository setorRepository;
  private final JdbcTemplate jdbcTemplate;

  public AssistantSetorViewRunner(SetorRepository setorRepository, JdbcTemplate jdbcTemplate) {
    this.setorRepository = setorRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(String... args) {
    this.setorRepository.findAll().forEach(setor -> {
      var codigo = SetorSupport.normalize(setor.getCodigo());
      if (codigo.isBlank()) {
        return;
      }

      createAssistantDocumentBlockView(codigo);
      createAssistantCacheView(codigo);
    });
  }

  private void createAssistantDocumentBlockView(String codigo) {
    var viewName = DOCUMENT_VIEW_PREFIX + toSqlSlug(codigo);
    this.jdbcTemplate.execute("drop view if exists " + viewName);
    var sql = """
        create view %s as
        select
          id,
          it_id,
          setor,
          documento,
          titulo,
          revisao,
          status,
          author,
          authorizer,
          print_date,
          create_date,
          page,
          step,
          section_number,
          section_title,
          entry_type,
          what,
          how,
          care,
          possible_causes,
          action_text,
          source_hash,
          created_at,
          updated_at
        from assistant_document_block
        where setor = '%s'
        order by documento, page nulls last, step nulls last, updated_at desc
        """.formatted(viewName, escapeSqlLiteral(codigo));

    this.jdbcTemplate.execute(sql);
  }

  private void createAssistantCacheView(String codigo) {
    var viewName = CACHE_VIEW_PREFIX + toSqlSlug(codigo);
    this.jdbcTemplate.execute("drop view if exists " + viewName);
    var sql = """
        create view %s as
        select
          id,
          it_id,
          setor,
          intent,
          normalized_question,
          document_version,
          model,
          documento,
          titulo,
          revisao,
          response_message,
          created_at,
          updated_at,
          last_accessed_at,
          hit_count
        from assistant_cache
        where setor = '%s'
        order by documento, updated_at desc
        """.formatted(viewName, escapeSqlLiteral(codigo));

    this.jdbcTemplate.execute(sql);
  }

  private static String toSqlSlug(String value) {
    var normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_+|_+$", "");

    return normalized.isBlank() ? "setor" : normalized;
  }

  private static String escapeSqlLiteral(String value) {
    return value.replace("'", "''");
  }
}
