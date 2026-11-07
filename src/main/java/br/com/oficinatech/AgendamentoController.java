package br.com.oficinatech;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agendamentos")
public class AgendamentoController {
  private final JdbcTemplate db;
  private final Core core;

  public AgendamentoController(JdbcTemplate db, Core core) { this.db = db; this.core = core; }

  static final String SELECT =
      "SELECT a.id,a.usuario_id,u.nome AS cliente,u.email,u.telefone,a.veiculo_id,v.marca,v.modelo,v.placa,a.servico,"
    + "a.data_servico::text AS data,to_char(a.horario,'HH24:MI') AS horario,a.problema,a.status,"
    + "o.descricao AS orc_descricao,o.mao_de_obra,o.valor_pecas,o.total,o.observacoes AS orc_obs,o.decisao,"
    + "(av.id IS NOT NULL) AS avaliado "
    + "FROM agendamentos a JOIN usuarios u ON u.id=a.usuario_id JOIN veiculos v ON v.id=a.veiculo_id "
    + "LEFT JOIN orcamentos o ON o.agendamento_id=a.id LEFT JOIN avaliacoes av ON av.agendamento_id=a.id ";

  @GetMapping
  public List<Map<String, Object>> listar(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String data,
                                          @RequestParam(required = false) Long id) {
    AuthUser u = Util.me();
    StringBuilder w = new StringBuilder("WHERE 1=1 ");
    List<Object> p = new ArrayList<>();
    if (!Util.isAdmin(u)) { w.append("AND a.usuario_id=? "); p.add(u.id()); }
    if (id != null) { w.append("AND a.id=? "); p.add(id); }
    if (data != null && !data.isBlank()) { w.append("AND a.data_servico=? "); p.add(java.sql.Date.valueOf(Util.date(data))); }
    if (q != null && !q.isBlank() && Util.isAdmin(u)) {
      w.append("AND (LOWER(u.nome) LIKE ? OR LOWER(u.email) LIKE ?) ");
      String like = "%" + q.toLowerCase().trim() + "%";
      p.add(like); p.add(like);
    }
    return db.queryForList(SELECT + w + "ORDER BY a.data_servico DESC, a.horario DESC, a.id DESC LIMIT 500", p.toArray());
  }

  @PostMapping
  public Map<String, Object> criar(@RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    if (Util.isAdmin(u)) throw new IllegalArgumentException("Agendamentos são solicitados pelos clientes.");
    Long vid = Util.lng(b, "veiculoId");
    if (vid == null) throw new IllegalArgumentException("Escolha um veículo.");
    Integer meu = db.queryForObject("SELECT COUNT(*) FROM veiculos WHERE id=? AND usuario_id=?", Integer.class, vid, u.id());
    if (meu == null || meu == 0) throw new NoSuchElementException("Veículo não encontrado.");
    String serv = Util.max(Util.req(b, "servico", "Escolha o serviço."), 100, "serviço");
    LocalDate d = Util.date(Util.req(b, "data", "Escolha a data."));
    if (d.isBefore(LocalDate.now())) throw new IllegalArgumentException("Escolha uma data a partir de hoje.");
    LocalTime t = LocalTime.parse(Util.req(b, "horario", "Escolha o horário."));
    String prob = Util.max(Util.s(b, "problema"), 2000, "descrição do problema");
    Long id = db.queryForObject(
        "INSERT INTO agendamentos(usuario_id,veiculo_id,servico,data_servico,horario,problema) VALUES(?,?,?,?,?,?) RETURNING id",
        Long.class, u.id(), vid, serv, java.sql.Date.valueOf(d), java.sql.Time.valueOf(t.withSecond(0)), prob);
    core.audit(u, "AGENDAMENTO_CRIADO", "Agendamento #" + id);
    core.notificarAdmins("Nova solicitação de serviço", core.nome(u.id()) + " solicitou " + serv + " para " + d + " às " + t.withSecond(0) + ".");
    return Map.of("id", id, "mensagem", "Solicitação enviada para análise da oficina.");
  }

  private Map<String, Object> proprio(long id) {
    AuthUser u = Util.me();
    var l = db.queryForList("SELECT usuario_id,status FROM agendamentos WHERE id=?", id);
    if (l.isEmpty() || ((Number) l.get(0).get("usuario_id")).longValue() != u.id()) throw new NoSuchElementException("Agendamento não encontrado.");
    return l.get(0);
  }

  @PostMapping("/{id}/orcamento/decisao")
  public Map<String, Object> decidir(@PathVariable long id, @RequestBody Map<String, Object> b) {
    proprio(id);
    String dec = Util.req(b, "decisao", "Informe a decisão.");
    if (!dec.equals("APROVADO") && !dec.equals("RECUSADO")) throw new IllegalArgumentException("Decisão inválida.");
    int n = db.update("UPDATE orcamentos SET decisao=?, decidido_em=NOW() WHERE agendamento_id=? AND decisao='PENDENTE'", dec, id);
    if (n == 0) throw new IllegalStateException("Não há orçamento pendente para este agendamento.");
    AuthUser u = Util.me();
    String ant = (String) db.queryForObject("SELECT status FROM agendamentos WHERE id=?", String.class, id);
    db.update("UPDATE agendamentos SET status=? WHERE id=?", dec, id);
    db.update("INSERT INTO historico_status(agendamento_id,status_anterior,status_novo,alterado_por) VALUES(?,?,?,?)", id, ant, dec, u.id());
    core.audit(u, "ORCAMENTO_" + dec, "Agendamento #" + id);
    core.notificarAdmins("Orçamento " + (dec.equals("APROVADO") ? "aprovado" : "recusado"),
        core.nome(u.id()) + " " + (dec.equals("APROVADO") ? "aprovou" : "recusou") + " o orçamento do agendamento #" + id + ".");
    return Map.of("mensagem", dec.equals("APROVADO") ? "Orçamento aprovado." : "Orçamento recusado.");
  }

  @PutMapping("/{id}/cancelar")
  public Map<String, Object> cancelar(@PathVariable long id) {
    String st = (String) proprio(id).get("status");
    if (!Set.of("SOLICITADO", "CONFIRMADO", "AGUARDANDO_APROVACAO").contains(st))
      throw new IllegalStateException("Este agendamento não pode mais ser cancelado. Fale com a oficina.");
    AuthUser u = Util.me();
    db.update("UPDATE agendamentos SET status='CANCELADO' WHERE id=?", id);
    db.update("INSERT INTO historico_status(agendamento_id,status_anterior,status_novo,alterado_por) VALUES(?,?,?,?)", id, st, "CANCELADO", u.id());
    core.audit(u, "AGENDAMENTO_CANCELADO", "Agendamento #" + id);
    core.notificarAdmins("Agendamento cancelado", core.nome(u.id()) + " cancelou o agendamento #" + id + ".");
    return Map.of("mensagem", "Agendamento cancelado.");
  }

  @PostMapping("/{id}/avaliacao")
  public Map<String, Object> avaliar(@PathVariable long id, @RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    if (!"CONCLUIDO".equals(proprio(id).get("status"))) throw new IllegalStateException("Só é possível avaliar serviços concluídos.");
    Integer ns = Util.intg(b, "notaServico"), na = Util.intg(b, "notaAtendimento");
    if (ns == null || na == null || ns < 1 || ns > 5 || na < 1 || na > 5) throw new IllegalArgumentException("As notas devem ser de 1 a 5.");
    try {
      db.update("INSERT INTO avaliacoes(agendamento_id,usuario_id,nota_servico,nota_atendimento,comentario) VALUES(?,?,?,?,?)",
          id, u.id(), ns, na, Util.max(Util.s(b, "comentario"), 2000, "comentário"));
    } catch (DuplicateKeyException e) {
      throw new IllegalStateException("Este serviço já foi avaliado.");
    }
    core.audit(u, "AVALIACAO", "Agendamento #" + id);
    core.notificarAdmins("Nova avaliação", core.nome(u.id()) + " avaliou o agendamento #" + id + " (serviço " + ns + "/5, atendimento " + na + "/5).");
    return Map.of("mensagem", "Obrigado pela avaliação!");
  }
}
