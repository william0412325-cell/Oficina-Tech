package br.com.oficinatech;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean
  public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean
  public SecurityFilterChain chain(HttpSecurity http, JwtService jwt) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
          + "script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; "
          + "base-uri 'self'; form-action 'self'")))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/auth/**", "/api/health").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADM")
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
        .addFilterBefore(new JwtFilter(jwt), UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(e -> e
            .authenticationEntryPoint((rq, rs, ex) -> {
              rs.setStatus(401);
              rs.setContentType("application/json;charset=UTF-8");
              rs.getWriter().write("{\"erro\":\"Não autenticado.\"}");
            })
            .accessDeniedHandler((rq, rs, ex) -> {
              rs.setStatus(403);
              rs.setContentType("application/json;charset=UTF-8");
              rs.getWriter().write("{\"erro\":\"Acesso negado.\"}");
            }));
    return http.build();
  }
}
