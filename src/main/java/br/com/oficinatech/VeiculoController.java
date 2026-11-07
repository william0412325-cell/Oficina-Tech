package br.com.oficinatech;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/veiculos")
public class VeiculoController {
  private final JdbcTemplate db;
  private final Core core;

  public VeiculoController(JdbcTemplate db, Core core) { this.db = db; this.core = core; }

  /** Retorna o dono do veículo; cliente só enxerga os próprios (404 caso contrário). */
  private long dono(long vid) {
    List<Long> l = db.queryForList("SELECT usuario_id FROM veiculos WHERE id=?", Long.class, vid);
    AuthUser u = Util.me();
    if (l.isEmpty() || (!Util.isAdmin(u) && l.get(0) != u.id())) throw new NoSuchElementException("Veículo não encontrado.");
    return l.get(0);
  }

  @GetMapping
  public List<Map<String, Object>> listar(@RequestParam(required = false) Long usuarioId) {
    AuthUser u = Util.me();
    long alvo = Util.isAdmin(u) ? (usuarioId == null ? -1 : usuarioId) : u.id();
    String sql = "SELECT v.id,v.usuario_id,v.marca,v.modelo,v.ano,v.placa,v.cor,v.km,v.foto_url AS foto,us.nome AS dono "
        + "FROM veiculos v JOIN usuarios us ON us.id=v.usuario_id" + (alvo >= 0 ? " WHERE v.usuario_id=?" : "") + " ORDER BY v.id DESC";
    return alvo >= 0 ? db.queryForList(sql, alvo) : db.queryForList(sql);
  }

  private Object[] campos(Map<String, Object> b) {
    String marca = Util.max(Util.req(b, "marca", "Informe a marca."), 60, "marca");
    String modelo = Util.max(Util.req(b, "modelo", "Informe o modelo."), 60, "modelo");
    Integer ano = Util.intg(b, "ano");
    if (ano != null && (ano < 1900 || ano > 2100)) throw new IllegalArgumentException("Ano inválido.");
    String placa = Util.max(Util.s(b, "placa"), 10, "placa");
    String cor = Util.max(Util.s(b, "cor"), 40, "cor");
    Integer km = Util.intg(b, "km");
    if (km != null && (km < 0 || km > 5_000_000)) throw new IllegalArgumentException("Quilometragem inválida.");
    return new Object[] {marca, modelo, ano, placa == null ? null : placa.toUpperCase(), cor, km};
  }

  @PostMapping
  public Map<String, Object> criar(@RequestBody Map<String, Object> b) {
    AuthUser u = Util.me();
    long dono = Util.isAdmin(u) ? Long.parseLong(Util.req(b, "usuarioId", "Informe o cliente.")) : u.id();
    Object[] c = campos(b);
    String foto = Util.foto(Util.s(b, "foto"));
    Long id = db.queryForObject(
        "INSERT INTO veiculos(usuario_id,marca,modelo,ano,placa,cor,km,foto_url) VALUES(?,?,?,?,?,?,?,?) RETURNING id",
        Long.class, dono, c[0], c[1], c[2], c[3], c[4], c[5], foto);
    core.audit(u, "VEICULO_CRIADO", "Veículo #" + id + " (" + c[0] + " " + c[1] + ") do cliente #" + dono);
    return Map.of("id", id, "mensagem", "Veículo cadastrado.");
  }

  @PutMapping("/{id}")
  public Map<String, Object> editar(@PathVariable long id, @RequestBody Map<String, Object> b) {
    dono(id);
    Object[] c = campos(b);
    db.update("UPDATE veiculos SET marca=?,modelo=?,ano=?,placa=?,cor=?,km=? WHERE id=?", c[0], c[1], c[2], c[3], c[4], c[5], id);
    if (b.containsKey("foto")) {
      String f = Util.s(b, "foto");
      db.update("UPDATE veiculos SET foto_url=? WHERE id=?", f == null ? null : Util.foto(f), id);
    }
    core.audit(Util.me(), "VEICULO_EDITADO", "Veículo #" + id);
    return Map.of("mensagem", "Veículo atualizado.");
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> excluir(@PathVariable long id) {
    dono(id);
    Integer n = db.queryForObject("SELECT COUNT(*) FROM agendamentos WHERE veiculo_id=?", Integer.class, id);
    if (n != null && n > 0) throw new IllegalStateException("Este veículo possui agendamentos e não pode ser excluído, para preservar o histórico.");
    db.update("DELETE FROM veiculos WHERE id=?", id);
    core.audit(Util.me(), "VEICULO_EXCLUIDO", "Veículo #" + id);
    return Map.of("mensagem", "Veículo excluído.");
  }

  @GetMapping("/{id}/historico")
  public List<Map<String, Object>> historico(@PathVariable long id) {
    dono(id);
    return db.queryForList(
        "SELECT id,categoria,data_servico::text AS data,descricao,pecas,valor,origem,registrado_por_nome AS por,registrado_por_perfil AS por_perfil "
      + "FROM historico_veiculo WHERE veiculo_id=? ORDER BY data_servico DESC, id DESC", id);
  }

  @PostMapping("/{id}/historico")
  public Map<String, Object> addHistorico(@PathVariable long id, @RequestBody Map<String, Object> b) {
    dono(id);
    AuthUser u = Util.me();
    boolean adm = Util.isAdmin(u);
    String cat = Util.req(b, "categoria", "Escolha a categoria.");
    if (!Set.of("OFICINA", "EXTERNO", "MODIFICACAO").contains(cat)) throw new IllegalArgumentException("Categoria inválida.");
    if ("OFICINA".equals(cat) && !adm) throw new IllegalArgumentException("Somente a oficina registra serviços feitos na Oficina Tech.");
    String origem = "OFICINA".equals(cat) ? "OFICINA_TECH"
        : ("MODIFICACAO".equals(cat) && adm && "OFICINA_TECH".equals(Util.s(b, "origem")) ? "OFICINA_TECH" : "EXTERNO");
    LocalDate data = Util.date(Util.req(b, "data", "Informe a data."));
    if (data.isAfter(LocalDate.now())) throw new IllegalArgumentException("A data não pode ser futura.");
    String desc = Util.max(Util.req(b, "descricao", "Descreva o serviço ou a alteração."), 2000, "descrição");
    String pecas = Util.max(Util.s(b, "pecas"), 500, "peças");
    db.update("INSERT INTO historico_veiculo(veiculo_id,categoria,data_servico,descricao,pecas,valor,origem,registrado_por,registrado_por_nome,registrado_por_perfil) "
        + "VALUES(?,?,?,?,?,?,?,?,?,?)", id, cat, java.sql.Date.valueOf(data), desc, pecas, Util.dec(b, "valor"), origem, u.id(), core.nome(u.id()), u.perfil());
    core.audit(u, "HISTORICO_VEICULO", "Registro (" + cat + ") no veículo #" + id);
    return Map.of("mensagem", "Registro adicionado ao histórico.");
  }

  @DeleteMapping("/historico/{hid}")
  public Map<String, Object> excluirHistorico(@PathVariable long hid) {
    AuthUser u = Util.me();
    var l = db.queryForList("SELECT veiculo_id,registrado_por FROM historico_veiculo WHERE id=?", hid);
    if (l.isEmpty()) throw new NoSuchElementException("Registro não encontrado.");
    dono(((Number) l.get(0).get("veiculo_id")).longValue());
    Number por = (Number) l.get(0).get("registrado_por");
    if (!Util.isAdmin(u) && (por == null || por.longValue() != u.id()))
      throw new IllegalArgumentException("Você só pode excluir registros feitos por você.");
    db.update("DELETE FROM historico_veiculo WHERE id=?", hid);
    core.audit(u, "HISTORICO_EXCLUIDO", "Registro #" + hid);
    return Map.of("mensagem", "Registro excluído.");
  }
}
