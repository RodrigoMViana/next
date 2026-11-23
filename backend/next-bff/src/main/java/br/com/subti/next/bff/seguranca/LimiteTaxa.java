package br.com.subti.next.bff.seguranca;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Limite de taxa em janela fixa de um minuto, por chave (usuário ou IP). Suficiente para uma instância;
 * com várias réplicas, mover para o Redis (pendência de infraestrutura).
 */
@Component
public class LimiteTaxa {

    private record Janela(long minuto, AtomicInteger contador) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock relogio;

    public LimiteTaxa() {
        this(Clock.systemUTC());
    }

    LimiteTaxa(Clock relogio) {
        this.relogio = relogio;
    }

    /** Registra uma tentativa; devolve false se o limite do minuto foi excedido. */
    public boolean permitir(String chave, int limitePorMinuto) {
        long minuto = relogio.millis() / 60_000;
        Janela j = janelas.compute(chave, (k, atual) -> atual == null || atual.minuto() != minuto
                ? new Janela(minuto, new AtomicInteger()) : atual);
        if (janelas.size() > 50_000) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto() != minuto);
        }
        return j.contador().incrementAndGet() <= limitePorMinuto;
    }
}
