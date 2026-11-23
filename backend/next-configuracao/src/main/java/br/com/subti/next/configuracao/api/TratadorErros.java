package br.com.subti.next.configuracao.api;

import br.com.subti.next.configuracao.domain.excecao.ErroCampo;
import br.com.subti.next.configuracao.domain.excecao.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Handler único do módulo: toda exceção vira Problem Details (RFC 9457) com correlationId e erros
 * por campo (RNF08, Arquitetura 6.5). Detalhes técnicos não são expostos.
 */
@RestControllerAdvice
public class TratadorErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);
    private static final String URN = "urn:next:erro:";

    @ExceptionHandler(NegocioException.class)
    ResponseEntity<ProblemDetail> negocio(NegocioException e, HttpServletRequest req) {
        return resposta(e.status(), e.getCodigo(), e.getTitulo(), e.getMessage(), e.getErros(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErroCampo> erros = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErroCampo(f.getField(), traduzir(f.getCode(), f.getDefaultMessage()))).toList();
        return resposta(400, "validacao", "Dados inválidos", "Corrija os campos destacados.", erros, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> restricao(ConstraintViolationException e, HttpServletRequest req) {
        List<ErroCampo> erros = e.getConstraintViolations().stream()
                .map(v -> new ErroCampo(v.getPropertyPath().toString(), v.getMessage())).toList();
        return resposta(400, "validacao", "Dados inválidos", "Corrija os campos destacados.", erros, req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> formato(Exception e, HttpServletRequest req) {
        return resposta(400, "requisicao-invalida", "Requisição inválida",
                "O corpo ou os parâmetros da requisição estão em formato inválido.", List.of(), req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> concorrencia(Exception e, HttpServletRequest req) {
        return resposta(412, "versao-desatualizada", "Registro alterado por outra pessoa",
                "O registro foi alterado depois que você o abriu. Recarregue a página e repita a alteração.", List.of(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integridade(DataIntegrityViolationException e, HttpServletRequest req) {
        LOG.warn("violação de integridade: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return resposta(409, "integridade", "Conflito de dados",
                "A operação viola uma regra de integridade (registro duplicado ou referência inválida).", List.of(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tamanho(Exception e, HttpServletRequest req) {
        return resposta(413, "arquivo-grande", "Arquivo muito grande", "O anexo deve ter no máximo 5 MB.",
                List.of(new ErroCampo("anexo", "O anexo deve ter no máximo 5 MB.")), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> naoEncontrado(Exception e, HttpServletRequest req) {
        return resposta(404, "rota-inexistente", "Rota inexistente", "A rota solicitada não existe.", List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e, HttpServletRequest req) {
        LOG.error("erro inesperado correlationId={}", MDC.get("correlationId"), e);
        return resposta(500, "erro-interno", "Erro interno",
                "Não foi possível concluir a operação. Informe o código de correlação ao suporte.", List.of(), req);
    }

    private static ResponseEntity<ProblemDetail> resposta(int status, String codigo, String titulo, String detalhe,
                                                          List<ErroCampo> erros, HttpServletRequest req) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detalhe);
        p.setType(URI.create(URN + codigo));
        p.setTitle(titulo);
        p.setInstance(URI.create(req.getRequestURI()));
        p.setProperty("correlationId", MDC.get("correlationId"));
        p.setProperty("errors", erros);
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }

    private static String traduzir(String codigo, String padrao) {
        if (codigo == null) {
            return padrao;
        }
        return switch (codigo) {
            case "NotBlank", "NotNull", "NotEmpty" -> "Campo obrigatório.";
            case "Size" -> "Tamanho acima do permitido.";
            case "Positive" -> "Informe um valor maior que zero.";
            case "PositiveOrZero" -> "Informe um valor maior ou igual a zero.";
            case "Min", "Max" -> "Valor fora do intervalo permitido.";
            default -> padrao;
        };
    }
}
