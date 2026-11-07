package br.com.oficinatech;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Util {
  private Util() {}

  public static final Map<String, String> STATUS = new LinkedHashMap<>();
  static {
    STATUS.put("SOLICITADO", "Solicitado");
    STATUS.put("CONFIRMADO", "Confirmado");
    STATUS.put("AGUARDANDO_APROVACAO", "Aguardando aprovação do orçamento");
    STATUS.put("APROVADO", "Orçamento aprovado");
    STATUS.put("EM_ANDAMENTO", "Em andamento");
    STATUS.put("AGUARDANDO_RETIRADA", "Aguardando retirada");
    STATUS.put("CONCLUIDO", "Concluído");
    STATUS.put("RECUSADO", "Orçamento recusado");
    STATUS.put("CANCELADO", "Cancelado");
  }

  public static AuthUser me() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof AuthUser u)) throw new IllegalStateException("Não autenticado.");
    return u;
  }

  public static boolean isAdmin(AuthUser u) { return "ADM".equals(u.perfil()); }

  /** Texto aparado ou null se vazio. */
  public static String s(Map<String, Object> m, String k) {
    Object v = m.get(k);
    if (v == null) return null;
    String t = v.toString().trim();
    return t.isEmpty() ? null : t;
  }

  /** Texto sem aparar (senhas). */
  public static String raw(Map<String, Object> m, String k) {
    Object v = m.get(k);
    return v == null ? null : v.toString();
  }

  public static String req(Map<String, Object> m, String k, String msg) {
    String v = s(m, k);
    if (v == null) throw new IllegalArgumentException(msg);
    return v;
  }

  public static String max(String v, int n, String campo) {
    if (v != null && v.length() > n) throw new IllegalArgumentException("O campo " + campo + " excede " + n + " caracteres.");
    return v;
  }

  public static BigDecimal dec(Map<String, Object> m, String k) {
    String v = s(m, k);
    if (v == null) return null;
    BigDecimal d = new BigDecimal(v.replace(",", "."));
    if (d.signum() < 0 || d.compareTo(new BigDecimal("99999999")) > 0) throw new IllegalArgumentException("Valor fora do permitido.");
    return d;
  }

  public static Integer intg(Map<String, Object> m, String k) {
    String v = s(m, k);
    return v == null ? null : Integer.valueOf(v);
  }

  public static Long lng(Map<String, Object> m, String k) {
    String v = s(m, k);
    return v == null ? null : Long.valueOf(v);
  }

  public static LocalDate date(String v) { return LocalDate.parse(v); }

  public static String html(String t) {
    return t == null ? "" : t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  /** Aceita apenas imagens jpeg/png/webp em data URL, até ~1,5 MB. */
  public static String foto(String v) {
    if (v == null) return null;
    boolean ok = v.startsWith("data:image/jpeg;base64,") || v.startsWith("data:image/png;base64,") || v.startsWith("data:image/webp;base64,");
    if (!ok || v.length() > 1_500_000) throw new IllegalArgumentException("Imagem inválida ou muito grande.");
    return v;
  }
}
