package br.com.subti.next.bff.modulos;

import br.com.subti.next.bff.seguranca.TokenInternoEmissor;
import br.com.subti.next.bff.seguranca.UsuarioSessao;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cliente HTTP do módulo Configuração: timeout explícito, token interno, X-Correlation-Id e circuit
 * breaker simples (5 falhas seguidas abrem o circuito por 30 s; Arquitetura 6.2 e 10.2).
 */
@Component
public class ClienteModulo {

    public static final String AUDIENCIA = "next-configuracao";

    /** Indisponibilidade do módulo (timeout, conexão recusada ou circuito aberto). */
    public static class ModuloIndisponivel extends RuntimeException {
        public ModuloIndisponivel(String msg) {
            super(msg);
        }
    }

    private final HttpClient http;
    private final String base;
    private final Duration timeout;
    private final boolean habilitado;
    private final TokenInternoEmissor emissor;
    private final AtomicInteger falhas = new AtomicInteger();
    private volatile Instant abertoAte = Instant.EPOCH;

    public ClienteModulo(@Value("${next.modulos.configuracao.url}") String base,
                         @Value("${next.modulos.configuracao.timeout-ms:15000}") long timeoutMs,
                         @Value("${next.modulos.configuracao.habilitado:true}") boolean habilitado,
                         TokenInternoEmissor emissor) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.habilitado = habilitado;
        this.emissor = emissor;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public boolean habilitado() {
        return habilitado;
    }

    public HttpResponse<byte[]> enviar(UsuarioSessao u, String metodo, String caminhoInterno, String query, byte[] corpo,
                                       Map<String, String> cabecalhos) {
        if (Instant.now().isBefore(abertoAte)) {
            throw new ModuloIndisponivel("circuito aberto");
        }
        URI uri = URI.create(base + caminhoInterno + (query == null || query.isBlank() ? "" : "?" + query));
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("X-Next-Internal-Token", emissor.emitir(u, AUDIENCIA))
                .header("X-Correlation-Id", MDC.get("correlationId") == null ? "c-bff" : MDC.get("correlationId"));
        cabecalhos.forEach((k, v) -> {
            if (v != null) {
                b.header(k, v);
            }
        });
        b.method(metodo, corpo == null || corpo.length == 0 ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(corpo));
        try {
            HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() >= 500) {
                registrarFalha();
            } else {
                falhas.set(0);
            }
            return r;
        } catch (IOException e) {
            registrarFalha();
            throw new ModuloIndisponivel(e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModuloIndisponivel("interrompido");
        }
    }

    private void registrarFalha() {
        if (falhas.incrementAndGet() >= 5) {
            abertoAte = Instant.now().plusSeconds(30);
            falhas.set(0);
        }
    }
}
