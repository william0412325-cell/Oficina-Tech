package br.com.oficinatech;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class NotificacaoController {
  private final JdbcTemplate db;

  public NotificacaoController(JdbcTemplate db) { this.db = db; }

  @GetMapping("/notificacoes")
  public Map<String, Object> listar() {
    long uid = Util.me().id();
    List<Map<String, Object>> itens = db.queryForList(
        "SELECT id,titulo,mensagem,lida,to_char(criado_em,'DD/MM/YYYY HH24:MI') AS quando FROM notificacoes WHERE usuario_id=? ORDER BY id DESC LIMIT 50", uid);
    Integer n = db.queryForObject("SELECT COUNT(*) FROM notificacoes WHERE usuario_id=? AND NOT lida", Integer.class, uid);
    return Map.of("itens", itens, "naoLidas", n == null ? 0 : n);
  }

  @PostMapping("/notificacoes/lidas")
  public Map<String, String> marcarTodas() {
    db.update("UPDATE notificacoes SET lida=TRUE WHERE usuario_id=? AND NOT lida", Util.me().id());
    return Map.of("mensagem", "ok");
  }

  @GetMapping("/lembretes")
  public List<Map<String, Object>> lembretes() {
    return db.queryForList(
        "SELECT l.id,l.descricao,l.data_prevista::text AS data,l.km_prevista,(l.enviado_em IS NOT NULL) AS avisado,v.marca,v.modelo,v.placa "
      + "FROM lembretes l JOIN veiculos v ON v.id=l.veiculo_id WHERE l.usuario_id=? ORDER BY l.data_prevista NULLS LAST, l.id DESC", Util.me().id());
  }
}
