package br.com.oficinatech;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+$");
  private static final Set<String> GENEROS = Set.of("Masculino", "Feminino", "Outro");
  private final JdbcTemplate db;
  private final PasswordEncoder enc;
  private final Core core;

  public MeController(JdbcTemplate db, PasswordEncoder enc, Core core) { this.db = db; this.enc = enc; this.core = core; }

  @GetMapping
  public Map<String, Object> get() { return core.usuario(Util.me().id()); }

  @PutMapping
  public Map<String, Object> atualizar(@RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    String nome = Util.max(Util.req(b, "nome", "Informe seu nome."), 100, "nome");
    String email = Util.max(Util.req(b, "email", "Informe o e-mail."), 150, "e-mail").toLowerCase();
    String tel = Util.max(Util.s(b, "telefone"), 25, "telefone");
    String gen = Util.s(b, "genero");
    if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("E-mail inválido.");
    if (gen != null && !GENEROS.contains(gen)) gen = null;
    Integer usado = db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email=? AND id<>?", Integer.class, email, u.id());
    if (usado != null && usado > 0) throw new IllegalArgumentException("Este e-mail já está em uso.");
    db.update("UPDATE usuarios SET nome=?,email=?,telefone=?,genero=? WHERE id=?", nome, email, tel, gen, u.id());
    if (b.containsKey("foto")) {
      String f = Util.s(b, "foto");
      db.update("UPDATE usuarios SET foto_url=? WHERE id=?", f == null ? null : Util.foto(f), u.id());
    }
    core.audit(u, "PERFIL_ATUALIZADO", "Dados do perfil atualizados");
    return core.usuario(u.id());
  }

  @PutMapping("/senha")
  public Map<String, String> senha(@RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    String atual = Util.raw(b, "atual"), nova = Util.raw(b, "nova");
    AuthController.validarSenha(nova);
    String hash = db.queryForObject("SELECT senha_hash FROM usuarios WHERE id=?", String.class, u.id());
    if (atual == null || !enc.matches(atual, hash)) throw new IllegalArgumentException("Senha atual incorreta.");
    db.update("UPDATE usuarios SET senha_hash=? WHERE id=?", enc.encode(nova), u.id());
    core.audit(u, "SENHA_ALTERADA", "Senha alterada pelo próprio usuário");
    return Map.of("mensagem", "Senha alterada com sucesso.");
  }
}
