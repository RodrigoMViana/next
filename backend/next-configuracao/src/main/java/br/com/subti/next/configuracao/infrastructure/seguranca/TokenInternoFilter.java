package br.com.subti.next.configuracao.infrastructure.seguranca;

import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida o token interno de curta duração assinado pelo BFF (Arquitetura 6.2 e 8.4) e propaga a
 * identidade e o X-Correlation-Id. Chamadas sem token válido são recusadas com 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TokenInternoFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Next-Internal-Token";
    public static final String EMISSOR = "next-bff";
    public static final String AUDIENCIA = "next-configuracao";

    private final byte[] segredo;

    public TokenInternoFilter(@Value("${next.seguranca.token-interno.segredo}") String segredo) {
        if (segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("next.seguranca.token-interno.segredo deve ter ao menos 32 bytes.");
        }
        this.segredo = segredo.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = req.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 64) {
            correlationId = "c-" + UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put("correlationId", correlationId);
        res.setHeader("X-Correlation-Id", correlationId);
        try {
            UsuarioAtual usuario = validar(req.getHeader(CABECALHO));
            if (usuario == null) {
                res.setStatus(401);
                res.setContentType("application/problem+json;charset=UTF-8");
                res.getWriter().write("{\"type\":\"urn:next:erro:token-interno-invalido\",\"title\":\"Não autenticado\","
                        + "\"status\":401,\"detail\":\"Chamada sem token interno válido do BFF.\",\"correlationId\":\""
                        + correlationId + "\"}");
                return;
            }
            UsuarioAtual.set(usuario);
            MDC.put("usuario", usuario.id());
            chain.doFilter(req, res);
        } finally {
            UsuarioAtual.limpar();
            MDC.clear();
        }
    }

    UsuarioAtual validar(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(segredo))) {
                return null;
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            Date agora = new Date();
            if (c.getExpirationTime() == null || c.getExpirationTime().before(agora)
                    || !EMISSOR.equals(c.getIssuer()) || c.getAudience() == null || !c.getAudience().contains(AUDIENCIA)) {
                return null;
            }
            List<String> perfis = c.getStringListClaim("perfis");
            String fabrica = c.getStringClaim("contratadaId");
            Set<String> conjunto = perfis == null ? Set.of() : new HashSet<>(perfis);
            return new UsuarioAtual(c.getSubject(), c.getStringClaim("nome"), Set.copyOf(conjunto),
                    fabrica == null || fabrica.isBlank() ? null : UUID.fromString(fabrica));
        } catch (Exception e) {
            return null;
        }
    }
}
