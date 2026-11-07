package br.com.oficinatech;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Rotas /api/admin/** — exigem perfil ADM (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private static final Set<String> GENEROS = Set.of("Masculino", "Feminino", "Outro");
  private static final Set<String> CANAIS = Set.of("SISTEMA", "EMAIL", "WHATSAPP", "TODOS");
  private final JdbcTemplate db;
  private final Core core;

  public AdminController(JdbcTemplate db, Core core) { this.db = db; this.core = core; }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    return db.queryForMap(
        "SELECT (SELECT COUNT(*) FROM usuarios WHERE perfil='CLIENTE') AS clientes,"
      + "(SELECT COUNT(*) FROM veiculos) AS veiculos,"
      + "(SELECT COUNT(*) FROM agendamentos WHERE status IN ('SOLICITADO','AGUARDANDO_APROVACAO')) AS pendentes,"
      + "(SELECT COUNT(*) FROM agendamentos WHERE data_servico=CURRENT_DATE AND status<>'CANCELADO') AS hoje,"
      + "(SELECT COUNT(*) FROM agendamentos WHERE status='CONCLUIDO') AS concluidos,"
      + "(SELECT COALESCE(ROUND(AVG((nota_servico+nota_atendimento)/2.0),1),0) FROM avaliacoes) AS media,"
      + "(SELECT COUNT(*) FROM atendimento_humano WHERE status='ABERTO') AS atendimentos");
  }

  // ---------- Clientes ----------
  @GetMapping("/clientes")
  public List<Map<String, Object>> clientes(@RequestParam(required = false) String q) {
    String like = "%" + (q == null ? "" : q.toLowerCase().trim()) + "%";
    List<Map<String, Object>> us = db.queryForList(
        "SELECT id,nome,email,telefone,genero,to_char(criado_em,'DD/MM/YYYY') AS desde FROM usuarios "
      + "WHERE perfil='CLIENTE' AND (LOWER(nome) LIKE ? OR LOWER(email) LIKE ?) ORDER BY nome LIMIT 300", like, like);
    Map<Long, List<Map<String, Object>>> por = new HashMap<>();
    if (!us.isEmpty()) {
      Object[] ids = us.stream().map(m -> ((Number) m.get("id")).longValue()).toArray();
      String in = String.join(",", Collections.nCopies(ids.length, "?"));
      for (Map<String, Object> v : db.queryForList(
          "SELECT id,usuario_id,marca,modelo,ano,placa,cor,km FROM veiculos WHERE usuario_id IN (" + in + ") ORDER BY id", ids))
        por.computeIfAbsent(((Number) v.get("usuario_id")).longValue(), k -> new ArrayList<>()).add(v);
    }
    for (Map<String, Object> m : us) m.put("veiculos", por.getOrDefault(((Number) m.get("id")).longValue(), List.of()));
    return us;
  }

  @PutMapping("/clientes/{id}")
  public Map<String, String> editarCliente(@PathVariable long id, @RequestBody Map<String, Object> b) {
    String nome = Util.max(Util.req(b, "nome", "Informe o nome."), 100, "nome");
    String email = Util.max(Util.req(b, "email", "Informe o e-mail."), 150, "e-mail").toLowerCase();
    String tel = Util.max(Util.s(b, "telefone"), 25, "telefone");
    String gen = Util.s(b, "genero");
    if (gen != null && !GENEROS.contains(gen)) gen = null;
    Integer usado = db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email=? AND id<>?", Integer.class, email, id);
    if (usado != null && usado > 0) throw new IllegalArgumentException("Este e-mail já está em uso.");
    int n = db.update("UPDATE usuarios SET nome=?,email=?,telefone=?,genero=? WHERE id=? AND perfil='CLIENTE'", nome, email, tel, gen, id);
    if (n == 0) throw new NoSuchElementException("Cliente não encontrado.");
    core.audit(Util.me(), "CLIENTE_EDITADO", "Cliente #" + id + " editado pelo administrador");
    return Map.of("mensagem", "Cliente atualizado.");
  }

  // ---------- Calendário e status ----------
  @GetMapping("/calendario")
  public List<Map<String, Object>> calendario(@RequestParam String mes) {
    YearMonth ym = YearMonth.parse(mes);
    return db.queryForList(
        "SELECT data_servico::text AS data, COUNT(*) AS total FROM agendamentos "
      + "WHERE data_servico>=? AND data_servico<=? AND status<>'CANCELADO' GROUP BY data_servico ORDER BY data_servico",
        java.sql.Date.valueOf(ym.atDay(1)), java.sql.Date.valueOf(ym.atEndOfMonth()));
  }

  @PutMapping("/agendamentos/{id}/status")
  public Map<String, String> status(@PathVariable long id, @RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    String novo = Util.req(b, "status", "Informe o status.");
    if (!Util.STATUS.containsKey(novo)) throw new IllegalArgumentException("Status inválido.");
    var r = db.queryForList("SELECT status,usuario_id,veiculo_id,servico,problema FROM agendamentos WHERE id=?", id);
    if (r.isEmpty()) throw new NoSuchElementException("Agendamento não encontrado.");
    String ant = (String) r.get(0).get("status");
    if (ant.equals(novo)) return Map.of("mensagem", "Status mantido.");
    long cli = ((Number) r.get(0).get("usuario_id")).longValue();
    db.update("UPDATE agendamentos SET status=? WHERE id=?", novo, id);
    db.update("INSERT INTO historico_status(agendamento_id,status_anterior,status_novo,alterado_por) VALUES(?,?,?,?)", id, ant, novo, u.id());
    core.audit(u, "STATUS_AGENDAMENTO", "Agendamento #" + id + ": " + ant + " -> " + novo);
    core.notificar(cli, "Atualização do seu serviço", "Seu agendamento #" + id + " agora está: " + Util.STATUS.get(novo) + ".");
    if ("CONCLUIDO".equals(novo)) {
      Integer ja = db.queryForObject("SELECT COUNT(*) FROM historico_veiculo WHERE agendamento_id=?", Integer.class, id);
      if (ja != null && ja == 0) {
        List<BigDecimal> tot = db.queryForList("SELECT total FROM orcamentos WHERE agendamento_id=? AND decisao='APROVADO'", BigDecimal.class, id);
        String pecas = db.queryForObject("SELECT string_agg(nome || COALESCE(' (' || marca || ')',''), ', ') FROM pecas WHERE agendamento_id=?", String.class, id);
        String desc = r.get(0).get("servico") + (r.get(0).get("problema") != null ? " — " + r.get(0).get("problema") : "");
        db.update("INSERT INTO historico_veiculo(veiculo_id,categoria,data_servico,descricao,pecas,valor,origem,registrado_por,registrado_por_nome,registrado_por_perfil,agendamento_id) "
            + "VALUES(?,'OFICINA',CURRENT_DATE,?,?,?,'OFICINA_TECH',?,?,'ADM',?)",
            r.get(0).get("veiculo_id"), desc, pecas, tot.isEmpty() ? null : tot.get(0), u.id(), core.nome(u.id()), id);
      }
      core.notificar(cli, "Serviço concluído", "Seu serviço foi concluído. Conte como foi pela avaliação em “Serviços e orçamentos”.");
    }
    return Map.of("mensagem", "Status atualizado.");
  }

  @PutMapping("/agendamentos/{id}/orcamento")
  public Map<String, String> orcamento(@PathVariable long id, @RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    var r = db.queryForList("SELECT status,usuario_id FROM agendamentos WHERE id=?", id);
    if (r.isEmpty()) throw new NoSuchElementException("Agendamento não encontrado.");
    String desc = Util.max(Util.req(b, "descricao", "Descreva o serviço do orçamento."), 2000, "descrição");
    BigDecimal mao = Optional.ofNullable(Util.dec(b, "maoDeObra")).orElse(BigDecimal.ZERO);
    BigDecimal pc = Optional.ofNullable(Util.dec(b, "valorPecas")).orElse(BigDecimal.ZERO);
    String obs = Util.max(Util.s(b, "observacoes"), 2000, "observações");
    db.update("INSERT INTO orcamentos(agendamento_id,descricao,mao_de_obra,valor_pecas,total,observacoes,decisao) VALUES(?,?,?,?,?,?,'PENDENTE') "
        + "ON CONFLICT (agendamento_id) DO UPDATE SET descricao=EXCLUDED.descricao,mao_de_obra=EXCLUDED.mao_de_obra,valor_pecas=EXCLUDED.valor_pecas,"
        + "total=EXCLUDED.total,observacoes=EXCLUDED.observacoes,decisao='PENDENTE',decidido_em=NULL",
        id, desc, mao, pc, mao.add(pc), obs);
    String st = (String) r.get(0).get("status");
    if (Set.of("SOLICITADO", "CONFIRMADO", "AGUARDANDO_APROVACAO", "RECUSADO").contains(st)) {
      db.update("UPDATE agendamentos SET status='AGUARDANDO_APROVACAO' WHERE id=?", id);
      if (!st.equals("AGUARDANDO_APROVACAO"))
        db.update("INSERT INTO historico_status(agendamento_id,status_anterior,status_novo,alterado_por) VALUES(?,?,?,?)", id, st, "AGUARDANDO_APROVACAO", u.id());
    }
    core.audit(u, "ORCAMENTO_ENVIADO", "Agendamento #" + id + " total " + mao.add(pc));
    core.notificar(((Number) r.get(0).get("usuario_id")).longValue(), "Novo orçamento disponível",
        "O orçamento do agendamento #" + id + " está disponível para sua aprovação.");
    return Map.of("mensagem", "Orçamento enviado ao cliente.");
  }

  // ---------- Peças ----------
  @GetMapping("/pecas")
  public List<Map<String, Object>> pecas() {
    return db.queryForList(
        "SELECT p.id,p.nome,p.marca,p.fornecedor,p.preco_compra,p.garantia,p.quantidade,p.agendamento_id,"
      + "CASE WHEN v.id IS NULL THEN NULL ELSE v.marca || ' ' || v.modelo || COALESCE(' · ' || v.placa,'') END AS veiculo "
      + "FROM pecas p LEFT JOIN veiculos v ON v.id=p.veiculo_id ORDER BY p.id DESC LIMIT 500");
  }

  @PostMapping("/pecas")
  public Map<String, String> addPeca(@RequestBody Map<String, Object> b) {
    String nome = Util.max(Util.req(b, "nome", "Informe o nome da peça."), 120, "nome");
    Long ag = Util.lng(b, "agendamentoId");
    Long veic = null;
    if (ag != null) {
      List<Long> l = db.queryForList("SELECT veiculo_id FROM agendamentos WHERE id=?", Long.class, ag);
      if (l.isEmpty()) throw new IllegalArgumentException("Agendamento informado não existe.");
      veic = l.get(0);
    }
    Integer qtd = Util.intg(b, "quantidade");
    db.update("INSERT INTO pecas(nome,marca,fornecedor,preco_compra,garantia,quantidade,agendamento_id,veiculo_id) VALUES(?,?,?,?,?,?,?,?)",
        nome, Util.max(Util.s(b, "marca"), 80, "marca"), Util.max(Util.s(b, "fornecedor"), 150, "fornecedor"), Util.dec(b, "precoCompra"),
        Util.max(Util.s(b, "garantia"), 80, "garantia"), qtd == null ? 0 : Math.max(qtd, 0), ag, veic);
    core.audit(Util.me(), "PECA_CRIADA", nome);
    return Map.of("mensagem", "Peça registrada.");
  }

  @DeleteMapping("/pecas/{id}")
  public Map<String, String> delPeca(@PathVariable long id) {
    db.update("DELETE FROM pecas WHERE id=?", id);
    core.audit(Util.me(), "PECA_EXCLUIDA", "Peça #" + id);
    return Map.of("mensagem", "Peça excluída.");
  }

  // ---------- Lembretes ----------
  @GetMapping("/lembretes")
  public List<Map<String, Object>> lembretes() {
    return db.queryForList(
        "SELECT l.id,l.descricao,l.data_prevista::text AS data,l.km_prevista,l.canal,l.antecedencia_dias,(l.enviado_em IS NOT NULL) AS avisado,"
      + "u.nome AS cliente,v.marca,v.modelo,v.placa,v.km FROM lembretes l JOIN usuarios u ON u.id=l.usuario_id JOIN veiculos v ON v.id=l.veiculo_id "
      + "ORDER BY l.enviado_em IS NOT NULL, l.data_prevista NULLS LAST, l.id DESC LIMIT 300");
  }

  @PostMapping("/lembretes")
  public Map<String, String> addLembrete(@RequestBody Map<String, Object> b) {
    long uid = Long.parseLong(Util.req(b, "usuarioId", "Escolha o cliente."));
    long vid = Long.parseLong(Util.req(b, "veiculoId", "Escolha o veículo."));
    Integer ok = db.queryForObject("SELECT COUNT(*) FROM veiculos WHERE id=? AND usuario_id=?", Integer.class, vid, uid);
    if (ok == null || ok == 0) throw new IllegalArgumentException("O veículo não pertence a este cliente.");
    String desc = Util.max(Util.req(b, "descricao", "Descreva a manutenção."), 160, "descrição");
    String data = Util.s(b, "dataPrevista");
    Integer km = Util.intg(b, "kmPrevista");
    if (data == null && km == null) throw new IllegalArgumentException("Informe a data prevista ou a quilometragem prevista.");
    String canal = Optional.ofNullable(Util.s(b, "canal")).orElse("SISTEMA");
    if (!CANAIS.contains(canal)) throw new IllegalArgumentException("Canal inválido.");
    Integer ant = Util.intg(b, "antecedenciaDias");
    db.update("INSERT INTO lembretes(usuario_id,veiculo_id,descricao,data_prevista,km_prevista,canal,antecedencia_dias,criado_por) VALUES(?,?,?,?,?,?,?,?)",
        uid, vid, desc, data == null ? null : java.sql.Date.valueOf(Util.date(data)), km, canal, ant == null ? 7 : Math.max(0, Math.min(ant, 90)), Util.me().id());
    core.audit(Util.me(), "LEMBRETE_CRIADO", desc + " (cliente #" + uid + ")");
    return Map.of("mensagem", "Lembrete cadastrado.");
  }

  @DeleteMapping("/lembretes/{id}")
  public Map<String, String> delLembrete(@PathVariable long id) {
    db.update("DELETE FROM lembretes WHERE id=?", id);
    core.audit(Util.me(), "LEMBRETE_EXCLUIDO", "Lembrete #" + id);
    return Map.of("mensagem", "Lembrete excluído.");
  }

  // ---------- Avaliações, auditoria, atendimento ----------
  @GetMapping("/avaliacoes")
  public List<Map<String, Object>> avaliacoes() {
    return db.queryForList(
        "SELECT av.id,u.nome AS cliente,a.servico,av.nota_servico,av.nota_atendimento,av.comentario,to_char(av.criado_em,'DD/MM/YYYY') AS quando "
      + "FROM avaliacoes av JOIN usuarios u ON u.id=av.usuario_id JOIN agendamentos a ON a.id=av.agendamento_id ORDER BY av.id DESC LIMIT 300");
  }

  @GetMapping("/auditoria")
  public List<Map<String, Object>> auditoria() {
    return db.queryForList("SELECT id,usuario_nome,acao,detalhe,to_char(criado_em,'DD/MM/YYYY HH24:MI') AS quando FROM auditoria ORDER BY id DESC LIMIT 200");
  }

  @GetMapping("/atendimentos")
  public List<Map<String, Object>> atendimentos() {
    return db.queryForList(
        "SELECT h.id,u.nome AS cliente,u.email,u.telefone,h.mensagem,h.status,to_char(h.criado_em,'DD/MM/YYYY HH24:MI') AS quando "
      + "FROM atendimento_humano h JOIN usuarios u ON u.id=h.usuario_id ORDER BY (h.status='ABERTO') DESC, h.id DESC LIMIT 200");
  }

  @PutMapping("/atendimentos/{id}/resolver")
  public Map<String, String> resolver(@PathVariable long id) {
    db.update("UPDATE atendimento_humano SET status='RESOLVIDO', resolvido_em=NOW() WHERE id=?", id);
    core.audit(Util.me(), "ATENDIMENTO_RESOLVIDO", "Atendimento #" + id);
    return Map.of("mensagem", "Atendimento marcado como resolvido.");
  }
}
