package br.com.oficinatech;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Operações comuns: auditoria, notificações internas e dados do usuário. */
@Component
public class Core {
  public final JdbcTemplate db;
  public final Mensageiro msg;

  public Core(JdbcTemplate db, Mensageiro msg) {
    this.db = db;
    this.msg = msg;
  }

  public String nome(long id) {
    List<String> l = db.queryForList("SELECT nome FROM usuarios WHERE id=?", String.class, id);
    return l.isEmpty() ? "?" : l.get(0);
  }

  public Map<String, Object> usuario(long id) {
    List<Map<String, Object>> l = db.queryForList(
        "SELECT id,nome,email,telefone,genero,foto_url AS foto,perfil FROM usuarios WHERE id=?", id);
    if (l.isEmpty()) throw new java.util.NoSuchElementException("Usuário não encontrado.");
    return l.get(0);
  }

  public void audit(AuthUser u, String acao, String detalhe) {
    db.update("INSERT INTO auditoria(usuario_id,usuario_nome,acao,detalhe) VALUES(?,?,?,?)",
        u == null ? null : u.id(), u == null ? "sistema" : nome(u.id()), acao, detalhe);
  }

  public void notificar(long usuarioId, String titulo, String texto) {
    db.update("INSERT INTO notificacoes(usuario_id,titulo,mensagem) VALUES(?,?,?)", usuarioId, titulo, texto);
  }

  public void notificarAdmins(String titulo, String texto) {
    db.update("INSERT INTO notificacoes(usuario_id,titulo,mensagem) SELECT id,?,? FROM usuarios WHERE perfil='ADM'", titulo, texto);
    String wa = Mensageiro.env("ADMIN_WHATSAPP");
    if (!wa.isEmpty()) msg.whatsapp(wa, titulo + ": " + texto);
  }
}
