package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.ContratoCalculos;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Item contratado: serviço, volume e valor. O valor total é calculado e não editável (RN02). */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "contrato_item")
public class ContratoItem {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_medida_id")
    private UnidadeMedida unidadeMedida;

    private BigDecimal volume;

    @Column(name = "valor_unitario")
    private BigDecimal valorUnitario;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    public ContratoItem(Servico servico, BigDecimal volume, BigDecimal valorUnitario) {
        this.servico = servico;
        this.unidadeMedida = servico.getUnidadeMedida();
        this.volume = volume;
        this.valorUnitario = valorUnitario;
        this.valorTotal = ContratoCalculos.valorItem(volume, valorUnitario);
    }
}
