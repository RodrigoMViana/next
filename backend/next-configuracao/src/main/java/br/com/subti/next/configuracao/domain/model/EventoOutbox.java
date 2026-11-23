package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Evento gravado na mesma transação do dado e publicado depois (transactional outbox, D49). */
@Getter
@Setter
@Entity
@Table(name = "evento_outbox")
public class EventoOutbox extends RegistroNovo {

    @Id
    private UUID id = UUID.randomUUID();

    private String tipo;
    private int versao = 1;

    @Column(name = "ocorrido_em")
    private Instant ocorridoEm = Instant.now();

    @Column(name = "correlation_id")
    private String correlationId;

    @Lob
    private String payload;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    private int tentativas;
}
