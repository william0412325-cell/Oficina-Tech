package br.com.oficinatech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria/atualiza o administrador a partir de ADMIN_EMAIL e ADMIN_PASSWORD (variáveis de ambiente). */
@Component
public class AdminSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
  private final JdbcTemplate db;
  private final PasswordEncoder enc;

  public AdminSeeder(JdbcTemplate db, PasswordEncoder enc) { this.db = db; this.enc = enc; }

  @Override
  public void run(ApplicationArguments args) {
    String email = Mensageiro.env("ADMIN_EMAIL").toLowerCase();
    String senha = System.getenv("ADMIN_PASSWORD");
    if (email.isEmpty() || senha == null || senha.isEmpty()) {
      log.warn("ADMIN_EMAIL/ADMIN_PASSWORD não definidos: nenhum administrador será criado.");
      return;
    }
    if (senha.length() < 10 || senha.length() > 72) {
      log.warn("ADMIN_PASSWORD deve ter entre 10 e 72 caracteres. Administrador não criado/atualizado.");
      return;
    }
    var l = db.queryForList("SELECT id,senha_hash FROM usuarios WHERE email=?", email);
    if (l.isEmpty()) {
      db.update("INSERT INTO usuarios(nome,email,senha_hash,perfil) VALUES('Administrador',?,?,'ADM')", email, enc.encode(senha));
      log.info("Administrador criado: {}", email);
    } else if (!enc.matches(senha, (String) l.get(0).get("senha_hash"))) {
      db.update("UPDATE usuarios SET senha_hash=?, perfil='ADM' WHERE email=?", enc.encode(senha), email);
      log.info("Senha do administrador atualizada a partir da variável de ambiente.");
    }
  }
}
