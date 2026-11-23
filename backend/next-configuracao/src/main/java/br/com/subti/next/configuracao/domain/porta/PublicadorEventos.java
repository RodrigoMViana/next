package br.com.subti.next.configuracao.domain.porta;

import br.com.subti.next.configuracao.domain.model.EventoOutbox;

/** Porta de publicação de eventos entre módulos (Redis Streams na arquitetura, R6). */
public interface PublicadorEventos {

    void publicar(EventoOutbox evento);

    String nome();
}
