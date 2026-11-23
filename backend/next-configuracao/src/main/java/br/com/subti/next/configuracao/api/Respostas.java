package br.com.subti.next.configuracao.api;

import java.net.URI;
import org.springframework.http.ResponseEntity;

/** Respostas com ETag (controle otimista) e Location em criações. */
final class Respostas {

    static final String IF_MATCH = "If-Match";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private Respostas() {
    }

    static <T> ResponseEntity<T> ok(T corpo, long versao) {
        return ResponseEntity.ok().eTag("\"" + versao + "\"").body(corpo);
    }

    static <T> ResponseEntity<T> criado(String caminho, Object id, T corpo, long versao) {
        return ResponseEntity.created(URI.create(caminho + "/" + id)).eTag("\"" + versao + "\"").body(corpo);
    }

    /** Repetição idempotente: devolve 200 com o recurso já criado. */
    static <T> ResponseEntity<T> repetido(T corpo, long versao) {
        return ResponseEntity.ok().header("Idempotent-Replayed", "true").eTag("\"" + versao + "\"").body(corpo);
    }
}
