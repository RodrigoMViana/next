package br.com.subti.next.configuracao.infrastructure.eventos;

import br.com.subti.next.configuracao.domain.model.EventoOutbox;
import br.com.subti.next.configuracao.domain.porta.PublicadorEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publicador padrão da fase 1: registra o evento no log estruturado. Substituir por Redis Streams
 * (next.eventos.publicador=redis) quando a infraestrutura estiver disponível — integração pendente.
 * O payload não é registrado no log para não expor dados (RNF05).
 */
@Component
@ConditionalOnProperty(name = "next.eventos.publicador", havingValue = "log", matchIfMissing = true)
public class PublicadorLog implements PublicadorEventos {

    private static final Logger LOG = LoggerFactory.getLogger(PublicadorLog.class);

    @Override
    public void publicar(EventoOutbox evento) {
        LOG.info("evento publicado tipo={} id={} correlationId={}", evento.getTipo(), evento.getId(),
                evento.getCorrelationId());
    }

    @Override
    public String nome() {
        return "log";
    }
}
