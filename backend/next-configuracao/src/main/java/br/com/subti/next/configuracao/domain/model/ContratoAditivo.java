package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.ContratoCalculos;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Aditivo de prazo e/ou de valor. Registro imutável: somente inclusão (RN05). */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "contrato_aditivo")
public class ContratoAditivo extends RegistroNovo {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "contrato_id")
    private UUID contratoId;

    private String numero;

    @Column(name = "aditivo_prazo")
    private boolean aditivoPrazo;

    @Column(name = "aditivo_valor")
    private boolean aditivoValor;

    @Column(name = "nova_vigencia_fim")
    private LocalDate novaVigenciaFim;

    @Column(name = "valor_acrescimo")
    private BigDecimal valorAcrescimo;

    @Column(name = "data_assinatura")
    private LocalDate dataAssinatura;

    private String justificativa;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_by_nome")
    private String createdByNome;

    @PrePersist
    void antesDeInserir() {
        createdAt = Instant.now();
        createdBy = UsuarioAtual.get().id();
        createdByNome = UsuarioAtual.get().nome();
    }

    public ContratoCalculos.Aditivo paraCalculo() {
        return new ContratoCalculos.Aditivo(dataAssinatura, aditivoPrazo ? novaVigenciaFim : null,
                aditivoValor ? valorAcrescimo : null);
    }
}
