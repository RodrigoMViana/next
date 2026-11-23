package br.com.subti.next.bff.modulos;

import br.com.subti.next.bff.seguranca.Rbac;
import br.com.subti.next.bff.seguranca.Sessao;
import br.com.subti.next.bff.seguranca.UsuarioSessao;
import br.com.subti.next.bff.web.ErroBff;
import br.com.subti.next.bff.web.Problema;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas /api/v1/configuracao/** do BFF. Aplica o RBAC por funcionalidade, propaga identidade e
 * cabeçalhos de concorrência/idempotência ao módulo e traduz erros internos (Arquitetura 4.2 e 6.2).
 */
@RestController
public class ConfiguracaoProxyController {

    static final String PREFIXO = "/api/v1/configuracao";
    private static final List<String> REPASSAR_REQ = List.of("Content-Type", "If-Match", "Idempotency-Key", "Accept");
    private static final List<String> REPASSAR_RESP = List.of("Content-Type", "ETag", "Content-Disposition",
            "Idempotent-Replayed", "X-Content-Type-Options");

    private static final long TAMANHO_MAXIMO = 6L * 1024 * 1024;

    private final ClienteModulo modulo;

    public ConfiguracaoProxyController(ClienteModulo modulo) {
        this.modulo = modulo;
    }

    @RequestMapping(PREFIXO + "/**")
    public ResponseEntity<byte[]> encaminhar(HttpServletRequest req) throws IOException {
        UsuarioSessao u = Sessao.usuario().orElseThrow(() -> new ErroBff(401, "nao-autenticado",
                "Sessão inexistente", "Entre para continuar."));
        if (!modulo.habilitado()) {
            throw new ErroBff(503, "modulo-desabilitado", "Módulo indisponível", "O módulo Configuração está desligado.");
        }
        String caminho = req.getRequestURI().substring(PREFIXO.length());
        if (caminho.isEmpty() || caminho.contains("..")) {
            throw new ErroBff(404, "rota-inexistente", "Rota inexistente", "A rota solicitada não existe.");
        }
        if (!Rbac.permitido(u, req.getMethod(), caminho)) {
            throw new ErroBff(403, "acesso-negado", "Acesso negado", "Seu perfil não permite acessar esta funcionalidade.");
        }
        if (req.getContentLengthLong() > TAMANHO_MAXIMO) {
            throw new ErroBff(413, "arquivo-grande", "Requisição muito grande", "O anexo deve ter no máximo 5 MB.", "anexo");
        }
        Map<String, String> cab = new LinkedHashMap<>();
        REPASSAR_REQ.forEach(h -> cab.put(h, req.getHeader(h)));
        byte[] corpo = req.getInputStream().readNBytes((int) TAMANHO_MAXIMO + 1);
        if (corpo.length > TAMANHO_MAXIMO) {
            throw new ErroBff(413, "arquivo-grande", "Requisição muito grande", "O anexo deve ter no máximo 5 MB.", "anexo");
        }
        HttpResponse<byte[]> r;
        try {
            r = modulo.enviar(u, req.getMethod(), "/internal/v1" + caminho, req.getQueryString(), corpo, cab);
        } catch (ClienteModulo.ModuloIndisponivel e) {
            throw new ErroBff(503, "modulo-indisponivel", "Módulo indisponível",
                    "O módulo Configuração não respondeu. Tente novamente em instantes.");
        }
        int status = r.statusCode();
        if (status >= 500 || status == 401) {
            // Não vaza detalhes internos; mantém o correlationId para suporte
            return ResponseEntity.status(status == 401 ? 502 : status).header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                    .body(Problema.json(status == 401 ? 502 : status, "erro-interno", "Erro interno",
                            "Não foi possível concluir a operação. Informe o código de correlação ao suporte.",
                            req.getRequestURI(), List.of()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        ResponseEntity.BodyBuilder b = ResponseEntity.status(status);
        for (String h : REPASSAR_RESP) {
            r.headers().firstValue(h).ifPresent(v -> b.header(h, v));
        }
        r.headers().firstValue("Location").ifPresent(v -> b.header("Location", v.replace("/internal/v1", PREFIXO)));
        byte[] corpoResposta = r.body();
        if (status >= 400 && r.headers().firstValue("Content-Type").orElse("").contains("problem")) {
            // Não expõe o caminho interno do módulo ao navegador
            corpoResposta = new String(corpoResposta, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\"/internal/v1", "\"" + PREFIXO).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return b.body(corpoResposta);
    }
}
