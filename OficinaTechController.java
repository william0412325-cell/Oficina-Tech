package br.com.oficinatech;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OficinaTechController {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public OficinaTechController(JdbcTemplate db){ this.db = db; }

  @PostMapping("/usuarios")
  public ResponseEntity<?> cadastrar(@RequestBody Map<String,String> body){
    String nome=body.get("nome"), email=body.get("email"), senha=body.get("senha");
    if(nome==null||email==null||senha==null||nome.isBlank()||email.isBlank()||senha.isBlank())
      return ResponseEntity.badRequest().body(Map.of("erro","Preencha nome, e-mail e senha."));
    try{
      db.update("INSERT INTO usuarios(nome,email,senha_hash,perfil) VALUES(?,?,?,'CLIENTE')",
        nome,email.toLowerCase(),encoder.encode(senha));
      return ResponseEntity.ok(Map.of("mensagem","Cliente cadastrado."));
    }catch(Exception e){ return ResponseEntity.badRequest().body(Map.of("erro","E-mail já cadastrado ou dados inválidos.")); }
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String,String> body){
    String email=body.get("email"), senha=body.get("senha");
    if(email==null||senha==null) return ResponseEntity.badRequest().body(Map.of("erro","Informe e-mail e senha."));
    var rows=db.queryForList("SELECT id,nome,email,senha_hash,perfil FROM usuarios WHERE email=?",email.toLowerCase());
    if(rows.isEmpty() || !encoder.matches(senha,(String)rows.get(0).get("senha_hash")))
      return ResponseEntity.status(401).body(Map.of("erro","Login inválido."));
    var u=rows.get(0);
    return ResponseEntity.ok(Map.of("id",u.get("id"),"nome",u.get("nome"),"email",u.get("email"),"perfil",u.get("perfil")));
  }

  @PostMapping("/veiculos")
  public ResponseEntity<?> veiculo(@RequestBody Map<String,Object> b){
    db.update("INSERT INTO veiculos(usuario_id,marca,modelo,ano,placa) VALUES(?,?,?,?,?)",
      b.get("usuarioId"),b.get("marca"),b.get("modelo"),b.get("ano"),b.get("placa"));
    return ResponseEntity.ok(Map.of("mensagem","Veículo cadastrado."));
  }

  @GetMapping("/veiculos/{usuarioId}")
  public List<Map<String,Object>> veiculos(@PathVariable long usuarioId){
    return db.queryForList("SELECT * FROM veiculos WHERE usuario_id=?",usuarioId);
  }

  @PostMapping("/agendamentos")
  public ResponseEntity<?> agendar(@RequestBody Map<String,Object> b){
    db.update("INSERT INTO agendamentos(usuario_id,veiculo_id,servico,data_servico,horario,problema) VALUES(?,?,?,?,?,?)",
      b.get("usuarioId"),b.get("veiculoId"),b.get("servico"),b.get("data"),b.get("horario"),b.get("problema"));
    return ResponseEntity.ok(Map.of("mensagem","Agendamento solicitado."));
  }

  @GetMapping("/agendamentos/cliente/{usuarioId}")
  public List<Map<String,Object>> agendaCliente(@PathVariable long usuarioId){
    return db.queryForList("SELECT a.*,v.marca,v.modelo,v.placa FROM agendamentos a JOIN veiculos v ON v.id=a.veiculo_id WHERE a.usuario_id=? ORDER BY a.id DESC",usuarioId);
  }

  @GetMapping("/admin/agendamentos")
  public List<Map<String,Object>> agendaAdmin(){
    return db.queryForList("SELECT a.*,u.nome cliente,v.marca,v.modelo,v.placa FROM agendamentos a JOIN usuarios u ON u.id=a.usuario_id JOIN veiculos v ON v.id=a.veiculo_id ORDER BY a.id DESC");
  }

  @PutMapping("/admin/agendamentos/{id}/status")
  public ResponseEntity<?> status(@PathVariable long id,@RequestBody Map<String,String> b){
    String novo=b.get("status");
    String anterior=db.queryForObject("SELECT status FROM agendamentos WHERE id=?",String.class,id);
    db.update("UPDATE agendamentos SET status=? WHERE id=?",novo,id);
    db.update("INSERT INTO historico_status(agendamento_id,status_anterior,status_novo) VALUES(?,?,?)",id,anterior,novo);
    return ResponseEntity.ok(Map.of("mensagem","Status atualizado."));
  }
}
