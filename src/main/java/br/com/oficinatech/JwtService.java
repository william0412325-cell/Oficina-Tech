package br.com.oficinatech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
  private final byte[] key;
  private final ObjectMapper om = new ObjectMapper();

  public JwtService() {
    String s = System.getenv("JWT_SECRET");
    if (s == null || s.length() < 32) {
      log.warn("JWT_SECRET ausente ou com menos de 32 caracteres. Usando chave temporária: as sessões caem a cada reinício.");
      byte[] r = new byte[48];
      new SecureRandom().nextBytes(r);
      key = r;
    } else key = s.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] hmac(String data) {
    try {
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(key, "HmacSHA256"));
      return m.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String criar(long id, String perfil) {
    try {
      String h = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
      String p = B64.encodeToString(om.writeValueAsBytes(Map.of(
          "sub", String.valueOf(id), "role", perfil,
          "exp", Instant.now().plusSeconds(12 * 3600L).getEpochSecond())));
      return h + "." + p + "." + B64.encodeToString(hmac(h + "." + p));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public AuthUser validar(String token) {
    try {
      String[] a = token.split("\\.");
      if (a.length != 3) return null;
      if (!MessageDigest.isEqual(hmac(a[0] + "." + a[1]), Base64.getUrlDecoder().decode(a[2]))) return null;
      JsonNode n = om.readTree(Base64.getUrlDecoder().decode(a[1]));
      if (n.path("exp").asLong() < Instant.now().getEpochSecond()) return null;
      return new AuthUser(Long.parseLong(n.path("sub").asText()), n.path("role").asText());
    } catch (Exception e) {
      return null;
    }
  }
}
