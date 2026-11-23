package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Histórico de alterações e registro de governança (RF16, RNF04): o que mudou, quem, quando e o valor
 * anterior. Registro imutável (somente inclusão).
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "historico_alteracao")
public class HistoricoAlteracao extends RegistroNovo {

    @Id
    private UUID id = UUID.randomUUID();

    private String entidade;

    @Column(name = "entidade_id")
    private String entidadeId;

    private String acao;
    private String campo;

    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Column(name = "valor_novo")
    private String valorNovo;

    private String justificativa;

    @Column(name = "usuario_id")
    private String usuarioId;

    @Column(name = "usuario_nome")
    private String usuarioNome;

    @Column(name = "ocorrido_em")
    private Instant ocorridoEm;

    @Column(name = "correlation_id")
    private String correlationId;
}
