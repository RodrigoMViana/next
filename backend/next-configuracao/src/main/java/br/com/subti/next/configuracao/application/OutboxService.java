package br.com.subti.next.configuracao.application;

import br.com.subti.next.configuracao.domain.model.EventoOutbox;
import br.com.subti.next.configuracao.domain.repositorio.EventoOutboxRepository;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava eventos de domínio na tabela de outbox, na mesma transação do dado (D49).
 * Nome no formato modulo.entidade.fato-no-passado (Arquitetura 6.3).
 */
@Service
public class OutboxService {

    private final EventoOutboxRepository repositorio;
    private final JsonMapper json;

    public OutboxService(EventoOutboxRepository repositorio, JsonMapper json) {
        this.repositorio = repositorio;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tipo, Map<String, ?> payload) {
        EventoOutbox e = new EventoOutbox();
        e.setTipo(tipo);
        e.setCorrelationId(MDC.get("correlationId"));
        e.setPayload(json.writeValueAsString(payload));
        repositorio.save(e);
    }
}
