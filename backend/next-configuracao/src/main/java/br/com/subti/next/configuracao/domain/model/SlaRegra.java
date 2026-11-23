package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.CalculoSla.FormaContagem;
import br.com.subti.next.configuracao.domain.regra.CalculoSla.UnidadeMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Regra parametrizável do SLA: classe, indicador, meta e forma de contagem (RN11 a RN13).
 * Não há número predefinido de níveis, medidas ou prazos.
 */
@Getter
@Setter
@Entity
@Table(name = "sla_regra")
public class SlaRegra {

    @Id
    private UUID id = UUID.randomUUID();

    private int ordem;

    /** Classe livre, por exemplo "Prioridade crítica" ou "Severidade 1". */
    private String classe;

    /** Indicador livre, por exemplo "Tempo de primeira resposta" ou "Disponibilidade mensal". */
    private String indicador;

    @Column(name = "meta_valor")
    private BigDecimal metaValor;

    @Enumerated(EnumType.STRING)
    @Column(name = "meta_unidade")
    private UnidadeMeta metaUnidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_contagem")
    private FormaContagem formaContagem;

    @ManyToOne
    @JoinColumn(name = "calendario_id")
    private Calendario calendario;

    @Column(name = "permite_pausa")
    private boolean permitePausa;

    private String observacao;

    public String resumo() {
        return classe + " / " + indicador + " = " + metaValor.stripTrailingZeros().toPlainString() + " "
                + metaUnidade + " (" + formaContagem + (calendario == null ? "" : ", " + calendario.getNome()) + ")";
    }

    public SlaRegra copia() {
        SlaRegra r = new SlaRegra();
        r.ordem = ordem;
        r.classe = classe;
        r.indicador = indicador;
        r.metaValor = metaValor;
        r.metaUnidade = metaUnidade;
        r.formaContagem = formaContagem;
        r.calendario = calendario;
        r.permitePausa = permitePausa;
        r.observacao = observacao;
        return r;
    }
}
