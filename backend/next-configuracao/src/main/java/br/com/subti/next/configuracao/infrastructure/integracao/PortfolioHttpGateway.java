package br.com.subti.next.configuracao.infrastructure.integracao;

import br.com.subti.next.configuracao.domain.porta.PortfolioGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP da API do Portfólio (SUBTI). Contrato esperado — a confirmar com a equipe do Portfólio
 * (docs/02-integracoes-pendentes.md):
 * <pre>
 *   GET {base}/sistemas?termo=...      -> [ { id, sigla, nome, situacao, areaResponsavel } ]
 *   GET {base}/sistemas/{id}           -> { id, sigla, nome, situacao, areaResponsavel }
 * </pre>
 * Timeout explícito e degradação: se o Portfólio não responder, a tela mostra o bloco como indisponível.
 */
@Component
@ConditionalOnProperty(name = "next.integracao.portfolio.modo", havingValue = "http")
public class PortfolioHttpGateway implements PortfolioGateway {

    private static final Logger LOG = LoggerFactory.getLogger(PortfolioHttpGateway.class);

    private final RestClient cliente;
    private final AtomicBoolean disponivel = new AtomicBoolean(true);

    public PortfolioHttpGateway(@Value("${next.integracao.portfolio.url}") String url,
                                @Value("${next.integracao.portfolio.token:}") String token,
                                @Value("${next.integracao.portfolio.timeout-ms:3000}") long timeoutMs) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient.Builder b = RestClient.builder().baseUrl(url).requestFactory(fabrica);
        if (!token.isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + token);
        }
        this.cliente = b.build();
    }

    @Override
    public List<SistemaPortfolio> listar(String termo) {
        try {
            SistemaPortfolio[] r = cliente.get().uri(u -> u.path("/sistemas").queryParamIfPresent("termo",
                    Optional.ofNullable(termo).filter(t -> !t.isBlank())).build()).retrieve().body(SistemaPortfolio[].class);
            disponivel.set(true);
            return r == null ? List.of() : Arrays.asList(r);
        } catch (RestClientException e) {
            disponivel.set(false);
            LOG.warn("Portfólio indisponível: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public Optional<SistemaPortfolio> buscar(UUID id) {
        try {
            SistemaPortfolio s = cliente.get().uri("/sistemas/{id}", id).retrieve().body(SistemaPortfolio.class);
            disponivel.set(true);
            return Optional.ofNullable(s);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            disponivel.set(false);
            LOG.warn("Portfólio indisponível: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public boolean disponivel() {
        return disponivel.get();
    }
}
