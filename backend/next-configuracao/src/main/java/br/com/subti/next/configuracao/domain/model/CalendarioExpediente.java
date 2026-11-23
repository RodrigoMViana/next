package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Intervalo de expediente de um dia da semana (1 = segunda ... 7 = domingo, ISO-8601). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "calendario_expediente")
public class CalendarioExpediente {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "dia_semana")
    private int diaSemana;

    @Convert(converter = HoraConverter.class)
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Convert(converter = HoraConverter.class)
    @Column(name = "hora_fim")
    private LocalTime horaFim;

    public CalendarioExpediente(int diaSemana, LocalTime horaInicio, LocalTime horaFim) {
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
    }
}
