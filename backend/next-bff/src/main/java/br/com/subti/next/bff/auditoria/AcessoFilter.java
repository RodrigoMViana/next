package br.com.subti.next.bff.auditoria;

import br.com.subti.next.bff.seguranca.LimiteTaxa;
import br.com.subti.next.bff.seguranca.Sessao;
import br.com.subti.next.bff.seguranca.UsuarioSessao;
import br.com.subti.next.bff.web.Problema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Executado depois do Spring Security em toda rota /api/**: duração absoluta da sessão (10 h),
 * limite de taxa por usuário e trilha de acesso LGPD (quem acessou o quê, quando, por qual rota e IP).
 */
@Configuration
public class AcessoFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AcessoFilter.class);

    /** Rotas que expõem dados pessoais (nomes de pessoas, auditoria, equipe, fiscais). */
    private static final List<Pattern> DADOS_PESSOAIS = List.of(Pattern.compile(".*/usuarios.*"),
            Pattern.compile(".*/equipe.*"), Pattern.compile(".*/fiscais.*"), Pattern.compile(".*/auditoria.*"),
            Pattern.compile(".*/historico.*"), Pattern.compile(".*/auth/login"), Pattern.compile("/api/v1/configuracao/contratos/[^/]+"));

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> filtroAcesso(TrilhaRepositorio trilha, LimiteTaxa limite,
                                                             @Value("${next.auth.duracao-absoluta:10h}") Duration absoluta,
                                                             @Value("${next.seguranca.limites.requisicoes-por-minuto:600}") int porMinuto) {
        OncePerRequestFilter f = new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !request.getRequestURI().startsWith("/api/");
            }

            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                long inicio = System.nanoTime();
                UsuarioSessao u = Sessao.usuario().orElse(null);
                try {
                    if (u != null && u.loginEm().plus(absoluta).isBefore(Instant.now())) {
                        HttpSession s = req.getSession(false);
                        if (s != null) {
                            s.invalidate();
                        }
                        SecurityContextHolder.clearContext();
                        Problema.escrever(res, req, 401, "sessao-expirada", "Sessão expirada",
                                "A sessão atingiu a duração máxima. Entre novamente.");
                        return;
                    }
                    String chave = u != null ? "u:" + u.id() : "ip:" + req.getRemoteAddr();
                    if (!limite.permitir(chave, porMinuto)) {
                        Problema.escrever(res, req, 429, "limite-de-requisicoes", "Muitas requisições",
                                "Aguarde alguns segundos e tente novamente.");
                        return;
                    }
                    chain.doFilter(req, res);
                } finally {
                    registrar(trilha, req, res, Sessao.usuario().orElse(u), inicio);
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> r = new FilterRegistrationBean<>(f);
        r.setOrder(0);
        r.addUrlPatterns("/api/*");
        return r;
    }

    static void registrar(TrilhaRepositorio trilha, HttpServletRequest req, HttpServletResponse res, UsuarioSessao u,
                          long inicio) {
        String rota = req.getRequestURI();
        if (rota.equals("/api/v1/auth/sessao") || rota.equals("/api/v1/auth/csrf")) {
            return;
        }
        try {
            String metodo = req.getMethod();
            String[] partes = rota.split("/");
            String modulo = partes.length > 3 ? partes[3] : "bff";
            String recurso = modulo.equals("configuracao") && partes.length > 4 ? partes[4] : (partes.length > 4 ? partes[4] : null);
            String acao = switch (metodo) {
                case "GET" -> rota.contains("/arquivo") ? "DOWNLOAD" : "LEITURA";
                case "POST" -> rota.endsWith("/login") ? "LOGIN" : rota.endsWith("/logout") ? "LOGOUT"
                        : rota.contains("/exportacoes") ? "EXPORTACAO" : "ESCRITA";
                case "PUT", "PATCH" -> "ALTERACAO";
                case "DELETE" -> "REMOCAO";
                default -> metodo;
            };
            boolean pessoal = DADOS_PESSOAIS.stream().anyMatch(p -> p.matcher(rota).matches());
            trilha.inserir(new TrilhaRepositorio.Registro(null, Instant.now(), u == null ? null : u.id(),
                    u == null ? null : u.nome(), u == null ? null : String.join(",", u.perfis()), metodo,
                    rota.length() > 400 ? rota.substring(0, 400) : rota, modulo, recurso, acao, res.getStatus(),
                    req.getRemoteAddr(), MDC.get("correlationId"), pessoal, (System.nanoTime() - inicio) / 1_000_000));
        } catch (RuntimeException e) {
            LOG.error("falha ao gravar trilha de acesso correlationId={}", MDC.get("correlationId"), e);
        }
    }
}
