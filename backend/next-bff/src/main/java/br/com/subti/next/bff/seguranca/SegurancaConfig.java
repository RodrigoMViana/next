package br.com.subti.next.bff.seguranca;

import br.com.subti.next.bff.web.Problema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Segurança da aplicação web (Arquitetura 8.1 a 8.3): sessão no servidor com cookie HttpOnly,
 * CSRF com cookie XSRF-TOKEN + cabeçalho X-XSRF-TOKEN, CSP estrita e cabeçalhos de segurança.
 */
@Configuration
public class SegurancaConfig {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, SecurityContextRepository repositorio) throws Exception {
        http.securityContext(c -> c.securityContextRepository(repositorio))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(f -> f.changeSessionId()))
                .csrf(c -> c.spa())
                .cors(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h.contentSecurityPolicy(p -> p.policyDirectives(CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> Problema.escrever(res, req, 401, "nao-autenticado",
                                "Sessão expirada ou inexistente", "Entre novamente para continuar."))
                        .accessDeniedHandler((req, res, ex) -> Problema.escrever(res, req, 403, "acesso-negado",
                                "Acesso negado", "Requisição recusada (sessão ou token anti-CSRF inválido).")));
        return http.build();
    }

    /** X-Correlation-Id gerado no BFF e propagado a logs, módulos e respostas (Arquitetura 6.2 e 10.1). */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> correlacao() {
        OncePerRequestFilter f = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String id = "c-" + UUID.randomUUID().toString().substring(0, 8);
                MDC.put("correlationId", id);
                req.setAttribute("correlationId", id);
                res.setHeader("X-Correlation-Id", id);
                try {
                    chain.doFilter(req, res);
                } finally {
                    MDC.clear();
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> r = new FilterRegistrationBean<>(f);
        r.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return r;
    }
}
