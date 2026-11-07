package br.com.oficinatech;

import java.time.DateTimeException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
  private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

  private static ResponseEntity<Map<String, String>> r(HttpStatus s, String m) {
    return ResponseEntity.status(s).body(Map.of("erro", m));
  }

  @ExceptionHandler(NumberFormatException.class)
  ResponseEntity<Map<String, String>> num(NumberFormatException e) { return r(HttpStatus.BAD_REQUEST, "Valor numérico inválido."); }

  @ExceptionHandler({DateTimeException.class, TypeMismatchException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Map<String, String>> invalido(Exception e) { return r(HttpStatus.BAD_REQUEST, "Dados inválidos. Confira data, horário e demais campos."); }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> ia(IllegalArgumentException e) { return r(HttpStatus.BAD_REQUEST, e.getMessage()); }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<Map<String, String>> nf(NoSuchElementException e) { return r(HttpStatus.NOT_FOUND, e.getMessage()); }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<Map<String, String>> is(IllegalStateException e) { return r(HttpStatus.CONFLICT, e.getMessage()); }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, String>> di(DataIntegrityViolationException e) {
    log.warn("Violação de integridade: {}", e.getMostSpecificCause().getMessage());
    return r(HttpStatus.CONFLICT, "Operação não permitida: dados duplicados, inválidos ou vinculados a outros registros.");
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<Map<String, String>> geral(RuntimeException e) {
    log.error("Erro inesperado", e);
    return r(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente.");
  }
}
