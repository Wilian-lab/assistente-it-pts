package com.wlilan.backend_assistent.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

@Configuration(proxyBeanMethods = false)
public class RenderDatasourceConfiguration {

  @Bean
  @Primary
  @ConditionalOnProperty(name = "DATABASE_URL")
  public DataSource renderDataSource(Environment environment) {
    var databaseUrl = firstNonBlank(environment.getProperty("DATABASE_URL"));
    var uri = URI.create(databaseUrl);
    var credentials = parseCredentials(uri.getUserInfo());

    var host = firstNonBlank(uri.getHost());
    var port = uri.getPort() > 0 ? uri.getPort() : 5432;
    var database = firstNonBlank(stripLeadingSlash(uri.getPath()), "assistant_db");
    var query = hasText(uri.getQuery()) ? "?" + uri.getQuery().trim() : "";

    var dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(firstNonBlank(
        environment.getProperty("SPRING_DATASOURCE_URL"),
        "jdbc:postgresql://" + host + ":" + port + "/" + database + query));
    dataSource.setUsername(firstNonBlank(
        environment.getProperty("SPRING_DATASOURCE_USERNAME"),
        credentials.username()));
    dataSource.setPassword(firstNonBlank(
        environment.getProperty("SPRING_DATASOURCE_PASSWORD"),
        credentials.password()));
    return dataSource;
  }

  private Credentials parseCredentials(String userInfo) {
    var normalized = firstNonBlank(userInfo);
    if (!hasText(normalized)) {
      return new Credentials("", "");
    }

    var separator = normalized.indexOf(':');
    if (separator < 0) {
      return new Credentials(decode(normalized), "");
    }

    var username = decode(normalized.substring(0, separator));
    var password = decode(normalized.substring(separator + 1));
    return new Credentials(username, password);
  }

  private String decode(String value) {
    return URLDecoder.decode(firstNonBlank(value), StandardCharsets.UTF_8);
  }

  private String stripLeadingSlash(String value) {
    var normalized = firstNonBlank(value);
    if (!hasText(normalized)) {
      return "";
    }
    return normalized.startsWith("/") ? normalized.substring(1) : normalized;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isBlank();
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (hasText(value)) {
        return value.trim();
      }
    }
    return "";
  }

  private record Credentials(String username, String password) {
  }
}
