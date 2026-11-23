package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evento já consumido: deduplicação pelo id do evento (consumidor idempotente). */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "evento_processado")
public class EventoProcessado extends RegistroNovo {

    @Id
    private UUID id;

    private String tipo;

    @Column(name = "processado_em")
    private Instant processadoEm;

    public EventoProcessado(UUID id, String tipo) {
        this.id = id;
        this.tipo = tipo;
        this.processadoEm = Instant.now();
    }
}
