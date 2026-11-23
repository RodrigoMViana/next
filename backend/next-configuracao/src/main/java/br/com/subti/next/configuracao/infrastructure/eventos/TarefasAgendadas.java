package br.com.subti.next.configuracao.infrastructure.eventos;

import br.com.subti.next.configuracao.application.SlaService;
import br.com.subti.next.configuracao.domain.model.EventoOutbox;
import br.com.subti.next.configuracao.domain.porta.PublicadorEventos;
import br.com.subti.next.configuracao.domain.repositorio.EventoOutboxRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publicação do outbox e encerramento diário de versões de SLA vencidas. */
@Component
public class TarefasAgendadas {

    private static final Logger LOG = LoggerFactory.getLogger(TarefasAgendadas.class);
    private static final int MAX_TENTATIVAS = 10;

    private final EventoOutboxRepository outbox;
    private final PublicadorEventos publicador;
    private final SlaService slaService;

    public TarefasAgendadas(EventoOutboxRepository outbox, PublicadorEventos publicador, SlaService slaService) {
        this.outbox = outbox;
        this.publicador = publicador;
        this.slaService = slaService;
    }

    /** Publica eventos pendentes; falhas ficam para nova tentativa (fila de erro após 10 tentativas). */
    @Scheduled(fixedDelayString = "${next.eventos.intervalo-ms:5000}")
    @Transactional
    public void publicarOutbox() {
        for (EventoOutbox e : outbox.findTop100ByPublicadoEmIsNullOrderByOcorridoEmAsc()) {
            if (e.getTentativas() >= MAX_TENTATIVAS) {
                continue;
            }
            try {
                publicador.publicar(e);
                e.setPublicadoEm(Instant.now());
            } catch (RuntimeException ex) {
                e.setTentativas(e.getTentativas() + 1);
                LOG.warn("falha ao publicar evento id={} tentativa={}", e.getId(), e.getTentativas());
            }
        }
    }

    @Scheduled(cron = "${next.sla.cron-encerramento:0 5 0 * * *}", zone = "America/Sao_Paulo")
    public void encerrarVersoesVencidas() {
        int total = slaService.encerrarVersoesVencidas();
        if (total > 0) {
            LOG.info("versões de SLA encerradas por fim de vigência: {}", total);
        }
    }
}
