package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Colunas de controle de toda tabela de negócio (Arquitetura 7.2). */
@Getter
@MappedSuperclass
public abstract class EntidadeBase {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    private Long version;

    @PrePersist
    void antesDeInserir() {
        createdAt = Instant.now();
        createdBy = UsuarioAtual.get().id();
    }

    @PreUpdate
    void antesDeAtualizar() {
        updatedAt = Instant.now();
        updatedBy = UsuarioAtual.get().id();
    }

    /** Versão bruta: nula enquanto o registro é novo (o Spring Data usa isso para decidir entre INSERT e UPDATE). */
    public Long getVersion() {
        return version;
    }

    /** Versão para controle otimista e ETag (0 antes da primeira gravação). */
    public long versao() {
        return version == null ? 0 : version;
    }

    /** Permite manter o identificador em migração/importação de dados (por exemplo, carga de demonstração). */
    public void definirId(UUID novoId) {
        if (createdAt != null) {
            throw new IllegalStateException("O identificador de um registro gravado não pode mudar.");
        }
        this.id = novoId;
    }

    /** ETag forte derivada da versão (controle otimista, RNF06). */
    public String etag() {
        return "\"" + versao() + "\"";
    }
}
