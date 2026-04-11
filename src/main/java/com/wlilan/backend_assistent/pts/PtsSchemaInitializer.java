package com.wlilan.backend_assistent.pts;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PtsSchemaInitializer implements ApplicationRunner {

  private static final List<String> TEXT_COLUMNS = List.of(
      "setor",
      "produto",
      "etapa",
      "item",
      "variavel",
      "classificacao",
      "unidade",
      "limite_inf",
      "limite_sup",
      "resp_coleta",
      "resp_analise",
      "frequencia",
      "ponto_coleta",
      "amostra",
      "metodo_analise",
      "tag",
      "tag_aspen",
      "acao_abaixo",
      "acao_acima",
      "fca",
      "vai_no_app",
      "documento_referencia");

  private final JdbcTemplate jdbcTemplate;

  public PtsSchemaInitializer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (String column : TEXT_COLUMNS) {
      this.jdbcTemplate.execute("ALTER TABLE IF EXISTS pts_record ALTER COLUMN " + column + " TYPE TEXT");
    }
  }
}
