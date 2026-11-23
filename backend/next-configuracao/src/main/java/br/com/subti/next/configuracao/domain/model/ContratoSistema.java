package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vínculo do contrato com sistema do Portfólio: UUID sem FK e cópia do nome (RN19). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contrato_sistema")
public class ContratoSistema {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "sistema_id")
    private UUID sistemaId;

    @Column(name = "sistema_nome")
    private String sistemaNome;

    public ContratoSistema(UUID sistemaId, String sistemaNome) {
        this.sistemaId = sistemaId;
        this.sistemaNome = sistemaNome;
    }
}
