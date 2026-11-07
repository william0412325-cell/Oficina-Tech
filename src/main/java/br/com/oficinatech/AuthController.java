package br.com.oficinatech;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+$");
  private static final Set<String> GENEROS = Set.of("Masculino", "Feminino", "Outro");
  private final JdbcTemplate db;
  private final PasswordEncoder enc;
  private final JwtService jwt;
  private final Core core;
  private final String dummy;
  private final Map<String, Deque<Long>> falhas = new ConcurrentHashMap<>();

  public AuthController(JdbcTemplate db, PasswordEncoder enc, JwtService jwt, Core core) {
    this.db = db; this.enc = enc; this.jwt = jwt; this.core = core;
    this.dummy = enc.encode("senha-ficticia-para-tempo-constante");
  }

  private boolean bloqueado(String chave) {
    Deque<Long> q = falhas.computeIfAbsent(chave, k -> new ArrayDeque<>());
    long agora = System.currentTimeMillis();
    synchronized (q) {
      while (!q.isEmpty() && agora - q.peekFirst() > 600_000) q.pollFirst();
      return q.size() >= 8;
    }
  }

  private void registrarFalha(String chave) {
    Deque<Long> q = falhas.computeIfAbsent(chave, k -> new ArrayDeque<>());
    synchronized (q) { q.addLast(System.currentTimeMillis()); }
  }

  private Map<String, Object> sessao(long id, String perfil) {
    return Map.of("token", jwt.criar(id, perfil), "usuario", core.usuario(id));
  }

  static String sha(String s) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  static void validarSenha(String s) {
    if (s == null || s.length() < 8) throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
    if (s.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("A senha é muito longa (máximo 72 bytes).");
  }

  @PostMapping("/register")
  public Map<String, Object> registrar(@RequestBody Map<String, Object> b) {
    String nome = Util.max(Util.req(b, "nome", "Informe seu nome."), 100, "nome");
    String email = Util.max(Util.req(b, "email", "Informe o e-mail."), 150, "e-mail").toLowerCase();
    String senha = Util.raw(b, "senha");
    String tel = Util.max(Util.s(b, "telefone"), 25, "telefone");
    String gen = Util.s(b, "genero");
    if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("E-mail inválido.");
    validarSenha(senha);
    if (gen != null && !GENEROS.contains(gen)) gen = null;
    try {
      Long id = db.queryForObject(
          "INSERT INTO usuarios(nome,email,telefone,genero,senha_hash,perfil) VALUES(?,?,?,?,?,'CLIENTE') RETURNING id",
          Long.class, nome, email, tel, gen, enc.encode(senha));
      core.audit(new AuthUser(id, "CLIENTE"), "CADASTRO", "Novo cliente cadastrado");
      return sessao(id, "CLIENTE");
    } catch (DuplicateKeyException e) {
      throw new IllegalArgumentException("Este e-mail já está cadastrado.");
    }
  }

  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody Map<String, Object> b) {
    String email = Util.s(b, "email");
    String senha = Util.raw(b, "senha");
    if (email == null || senha == null) throw new IllegalArgumentException("Informe e-mail e senha.");
    email = email.toLowerCase();
    if (bloqueado("l:" + email)) throw new IllegalStateException("Muitas tentativas. Aguarde alguns minutos e tente novamente.");
    var rows = db.queryForList("SELECT id,senha_hash,perfil FROM usuarios WHERE email=?", email);
    String hash = rows.isEmpty() ? dummy : (String) rows.get(0).get("senha_hash");
    boolean ok = senha.getBytes(StandardCharsets.UTF_8).length <= 72 && enc.matches(senha, hash) && !rows.isEmpty();
    if (!ok) {
      registrarFalha("l:" + email);
      throw new IllegalArgumentException("E-mail ou senha incorretos.");
    }
    long id = ((Number) rows.get(0).get("id")).longValue();
    String perfil = (String) rows.get(0).get("perfil");
    falhas.remove("l:" + email);
    core.audit(new AuthUser(id, perfil), "LOGIN", "Login realizado");
    return sessao(id, perfil);
  }

  @PostMapping("/forgot")
  public Map<String, String> esqueci(@RequestBody Map<String, Object> b) {
    Map<String, String> resp = Map.of("mensagem", "Se o e-mail estiver cadastrado, enviaremos as instruções de recuperação.");
    String email = Util.s(b, "email");
    if (email == null) return resp;
    email = email.toLowerCase();
    if (bloqueado("f:" + email)) return resp;
    registrarFalha("f:" + email);
    var r = db.queryForList("SELECT id,nome FROM usuarios WHERE email=? AND perfil='CLIENTE'", email);
    if (r.isEmpty()) return resp;
    long id = ((Number) r.get(0).get("id")).longValue();
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String tok = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    db.update("UPDATE usuarios SET reset_hash=?, reset_expira=NOW()+INTERVAL '1 hour' WHERE id=?", sha(tok), id);
    String base = Mensageiro.env("APP_URL");
    String url = (base.isEmpty() ? "" : base.replaceAll("/+$", "")) + "/?reset=" + tok;
    if (core.msg.emailAtivo()) {
      core.msg.email(email, "Recuperação de senha - Oficina Tech",
          "Olá, " + r.get(0).get("nome") + "!\n\nPara criar uma nova senha, acesse o link abaixo (válido por 1 hora):\n"
              + url + "\n\nSe você não pediu isso, ignore este e-mail.");
    } else {
      log.warn("E-mail não configurado (RESEND_API_KEY/MAIL_FROM). Link de recuperação de {}: {}", email, url);
    }
    return resp;
  }

  @PostMapping("/reset")
  public Map<String, String> redefinir(@RequestBody Map<String, Object> b) {
    String tok = Util.req(b, "token", "Link inválido ou expirado.");
    String senha = Util.raw(b, "senha");
    validarSenha(senha);
    var r = db.queryForList("SELECT id FROM usuarios WHERE reset_hash=? AND reset_expira>NOW()", sha(tok));
    if (r.isEmpty()) throw new IllegalArgumentException("Link inválido ou expirado. Solicite uma nova recuperação.");
    long id = ((Number) r.get(0).get("id")).longValue();
    db.update("UPDATE usuarios SET senha_hash=?, reset_hash=NULL, reset_expira=NULL WHERE id=?", enc.encode(senha), id);
    core.audit(new AuthUser(id, "CLIENTE"), "SENHA_REDEFINIDA", "Senha redefinida por link de recuperação");
    return Map.of("mensagem", "Senha atualizada. Entre com a nova senha.");
  }
}
