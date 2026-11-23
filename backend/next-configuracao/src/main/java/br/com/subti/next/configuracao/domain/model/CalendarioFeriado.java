package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Feriado do calendário, classificado por abrangência e com recorrência anual opcional (RN17). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "calendario_feriado")
public class CalendarioFeriado {

    public enum Abrangencia { NACIONAL, ESTADUAL, MUNICIPAL, PONTO_FACULTATIVO }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "data_feriado")
    private LocalDate dataFeriado;

    private String descricao;

    @Enumerated(EnumType.STRING)
    private Abrangencia abrangencia;

    @Column(name = "recorrente_anual")
    private boolean recorrenteAnual;

    public CalendarioFeriado(LocalDate data, String descricao, Abrangencia abrangencia, boolean recorrenteAnual) {
        this.dataFeriado = data;
        this.descricao = descricao;
        this.abrangencia = abrangencia;
        this.recorrenteAnual = recorrenteAnual;
    }
}
