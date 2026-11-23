package br.com.subti.next.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Tradução de erros do BFF para Problem Details sem vazar detalhes técnicos (Arquitetura 4.2). */
@RestControllerAdvice
public class TratadorErrosBff {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErrosBff.class);

    @ExceptionHandler(ErroBff.class)
    ResponseEntity<String> bff(ErroBff e, HttpServletRequest req) {
        List<Map<String, String>> erros = e.getCampo() == null ? List.of()
                : List.of(Map.of("field", e.getCampo(), "message", e.getMessage()));
        return resposta(e.getStatus(), Problema.json(e.getStatus(), e.getCodigo(), e.getTitulo(), e.getMessage(),
                req.getRequestURI(), erros));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<String> validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<Map<String, String>> erros = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", "Campo obrigatório ou inválido.")).toList();
        return resposta(400, Problema.json(400, "validacao", "Dados inválidos", "Corrija os campos destacados.",
                req.getRequestURI(), erros));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<String> formato(Exception e, HttpServletRequest req) {
        return resposta(400, Problema.json(400, "requisicao-invalida", "Requisição inválida",
                "O corpo ou os parâmetros estão em formato inválido.", req.getRequestURI(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<String> inesperado(Exception e, HttpServletRequest req) {
        LOG.error("erro inesperado no BFF correlationId={}", MDC.get("correlationId"), e);
        return resposta(500, Problema.json(500, "erro-interno", "Erro interno",
                "Não foi possível concluir a operação. Informe o código de correlação ao suporte.", req.getRequestURI(),
                List.of()));
    }

    private static ResponseEntity<String> resposta(int status, String corpo) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(corpo);
    }
}
