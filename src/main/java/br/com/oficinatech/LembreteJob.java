package br.com.oficinatech;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Verifica lembretes pendentes a cada hora (data próxima ou quilometragem próxima) e avisa o cliente uma única vez. */
@Component
public class LembreteJob {
  private static final Logger log = LoggerFactory.getLogger(LembreteJob.class);
  private final JdbcTemplate db;
  private final Core core;

  public LembreteJob(JdbcTemplate db, Core core) { this.db = db; this.core = core; }

  @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
  public void enviar() {
    try {
      List<Map<String, Object>> l = db.queryForList(
          "SELECT l.id,l.usuario_id,l.descricao,l.data_prevista::text AS data,l.km_prevista,l.canal,"
        + "v.marca,v.modelo,v.placa,u.nome,u.email,u.telefone "
        + "FROM lembretes l JOIN veiculos v ON v.id=l.veiculo_id JOIN usuarios u ON u.id=l.usuario_id "
        + "WHERE l.enviado_em IS NULL AND ("
        + " (l.data_prevista IS NOT NULL AND l.data_prevista <= CURRENT_DATE + l.antecedencia_dias)"
        + " OR (l.km_prevista IS NOT NULL AND v.km IS NOT NULL AND v.km >= l.km_prevista - 500))");
      for (Map<String, Object> r : l) {
        long uid = ((Number) r.get("usuario_id")).longValue();
        String veic = r.get("marca") + " " + r.get("modelo") + (r.get("placa") != null ? " (" + r.get("placa") + ")" : "");
        String quando = r.get("data") != null ? "prevista para " + dBR((String) r.get("data"))
            : "prevista aos " + r.get("km_prevista") + " km";
        String texto = "Lembrete de manutenção: " + r.get("descricao") + " do veículo " + veic + ", " + quando
            + ". Agende pela Oficina Tech.";
        core.notificar(uid, "Lembrete de manutenção", texto);
        String canal = (String) r.get("canal");
        if ("EMAIL".equals(canal) || "TODOS".equals(canal))
          core.msg.email((String) r.get("email"), "Lembrete de manutenção - Oficina Tech", "Olá, " + r.get("nome") + "!\n\n" + texto);
        if ("WHATSAPP".equals(canal) || "TODOS".equals(canal))
          core.msg.whatsapp((String) r.get("telefone"), "Oficina Tech: " + texto);
        db.update("UPDATE lembretes SET enviado_em=NOW() WHERE id=?", r.get("id"));
      }
      if (!l.isEmpty()) log.info("{} lembrete(s) enviado(s).", l.size());
    } catch (Exception e) {
      log.error("Falha no job de lembretes", e);
    }
  }

  private static String dBR(String iso) {
    String[] p = iso.split("-");
    return p.length == 3 ? p[2] + "/" + p[1] + "/" + p[0] : iso;
  }
}
