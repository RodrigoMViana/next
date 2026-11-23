package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Alteração contratual da empresa, com anexo (RF08). Registro imutável: somente inclusão. */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "contratada_alteracao")
public class ContratadaAlteracao extends RegistroNovo {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "contratada_id")
    private UUID contratadaId;

    @Column(name = "data_alteracao")
    private LocalDate dataAlteracao;

    private String tipo;
    private String descricao;

    @Column(name = "anexo_nome")
    private String anexoNome;

    @Column(name = "anexo_tipo")
    private String anexoTipo;

    @Column(name = "anexo_tamanho")
    private Long anexoTamanho;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "anexo_conteudo")
    private byte[] anexoConteudo;

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
}
