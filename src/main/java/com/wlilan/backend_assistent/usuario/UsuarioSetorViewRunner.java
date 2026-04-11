package com.wlilan.backend_assistent.usuario;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.wlilan.backend_assistent.Security.SetorSupport;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class UsuarioSetorViewRunner implements CommandLineRunner {

  private static final String VIEW_PREFIX = "vw_usuarios_";

  private final SetorRepository setorRepository;
  private final JdbcTemplate jdbcTemplate;

  public UsuarioSetorViewRunner(SetorRepository setorRepository, JdbcTemplate jdbcTemplate) {
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

      var viewName = VIEW_PREFIX + toSqlSlug(codigo);
      var sql = """
          create or replace view %s as
          select
            u.id,
            u.name,
            u.email,
            u.role,
            u.cargo,
            s.codigo as setor,
            u.last_trained_it,
            u.last_training_date,
            u.next_training_date,
            u.retraining_interval_days
          from usuario u
          join usuario_setor us on us.usuario_id = u.id
          join setor s on s.id = us.setor_id
          where s.codigo = '%s'
          order by
            case when u.role = 'ADMIN' then 0 else 1 end,
            u.name
          """.formatted(viewName, escapeSqlLiteral(codigo));

      this.jdbcTemplate.execute(sql);
    });
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
