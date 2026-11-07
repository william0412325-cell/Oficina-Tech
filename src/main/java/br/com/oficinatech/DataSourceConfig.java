package br.com.oficinatech;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lê DATABASE_URL (formato do Render: postgresql://usuario:senha@host:5432/banco). */
@Configuration
public class DataSourceConfig {
  @Bean
  public DataSource dataSource() {
    HikariConfig c = new HikariConfig();
    String url = System.getenv("DATABASE_URL");
    if (url != null && !url.isBlank()) {
      if (url.startsWith("jdbc:")) {
        c.setJdbcUrl(url);
        c.setUsername(env("DB_USER", ""));
        c.setPassword(env("DB_PASSWORD", ""));
      } else {
        URI u = URI.create(url.trim());
        String[] ui = u.getUserInfo().split(":", 2);
        c.setUsername(ui[0]);
        c.setPassword(ui.length > 1 ? ui[1] : "");
        int port = u.getPort() == -1 ? 5432 : u.getPort();
        String jdbc = "jdbc:postgresql://" + u.getHost() + ":" + port + u.getPath();
        // URL externa do Render (host com ponto) exige SSL; a interna não.
        if (u.getHost().contains(".")) jdbc += "?sslmode=require";
        c.setJdbcUrl(jdbc);
      }
    } else {
      c.setJdbcUrl(env("JDBC_URL", "jdbc:postgresql://localhost:5432/oficina_tech"));
      c.setUsername(env("DB_USER", "postgres"));
      c.setPassword(env("DB_PASSWORD", "postgres"));
    }
    c.setMaximumPoolSize(5);
    c.setConnectionInitSql("SET TIME ZONE 'America/Sao_Paulo'");
    return new HikariDataSource(c);
  }

  private static String env(String k, String d) {
    String v = System.getenv(k);
    return v == null || v.isBlank() ? d : v;
  }
}
