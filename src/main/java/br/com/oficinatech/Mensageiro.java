package br.com.oficinatech;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Envio de e-mail (Resend) e WhatsApp (Cloud API da Meta). Só age se as variáveis estiverem configuradas. */
@Component
public class Mensageiro {
  private static final Logger log = LoggerFactory.getLogger(Mensageiro.class);
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final ObjectMapper om = new ObjectMapper();
  private final String resendKey = env("RESEND_API_KEY"), mailFrom = env("MAIL_FROM");
  private final String waToken = env("WHATSAPP_TOKEN"), waPhone = env("WHATSAPP_PHONE_ID"), waTemplate = env("WHATSAPP_TEMPLATE");

  public static String env(String k) {
    String v = System.getenv(k);
    return v == null ? "" : v.trim();
  }

  public boolean emailAtivo() { return !resendKey.isEmpty() && !mailFrom.isEmpty(); }

  public boolean whatsappAtivo() { return !waToken.isEmpty() && !waPhone.isEmpty(); }

  public void email(String para, String assunto, String texto) {
    if (!emailAtivo() || para == null || para.isBlank()) return;
    CompletableFuture.runAsync(() -> {
      try {
        String corpo = Util.html(texto).replaceAll("(https?://[^\\s<]+)", "<a href=\"$1\">$1</a>").replace("\n", "<br>");
        String html = "<div style=\"font-family:Arial,sans-serif;font-size:15px;color:#172538\">" + corpo + "</div>";
        String json = om.writeValueAsString(Map.of("from", mailFrom, "to", List.of(para), "subject", assunto, "html", html));
        HttpRequest rq = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + resendKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        HttpResponse<String> rs = http.send(rq, HttpResponse.BodyHandlers.ofString());
        if (rs.statusCode() >= 300) log.warn("Falha ao enviar e-mail: HTTP {} {}", rs.statusCode(), rs.body());
      } catch (Exception e) {
        log.warn("Falha ao enviar e-mail: {}", e.toString());
      }
    });
  }

  public void whatsapp(String telefone, String texto) {
    if (!whatsappAtivo() || telefone == null) return;
    String num = telefone.replaceAll("\\D", "");
    if (num.length() == 10 || num.length() == 11) num = "55" + num;
    if (num.length() < 12) return;
    final String to = num;
    CompletableFuture.runAsync(() -> {
      try {
        Map<String, Object> corpo;
        if (!waTemplate.isEmpty()) {
          String t = texto.replaceAll("\\s+", " ");
          corpo = Map.of("messaging_product", "whatsapp", "to", to, "type", "template",
              "template", Map.of("name", waTemplate, "language", Map.of("code", "pt_BR"),
                  "components", List.of(Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", t))))));
        } else {
          corpo = Map.of("messaging_product", "whatsapp", "to", to, "type", "text", "text", Map.of("body", texto));
        }
        HttpRequest rq = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/v21.0/" + waPhone + "/messages"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + waToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(corpo))).build();
        HttpResponse<String> rs = http.send(rq, HttpResponse.BodyHandlers.ofString());
        if (rs.statusCode() >= 300) log.warn("Falha ao enviar WhatsApp: HTTP {} {}", rs.statusCode(), rs.body());
      } catch (Exception e) {
        log.warn("Falha ao enviar WhatsApp: {}", e.toString());
      }
    });
  }
}
