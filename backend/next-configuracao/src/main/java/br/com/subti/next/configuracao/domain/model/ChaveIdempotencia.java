package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chave de idempotência de criações críticas (RNF06): a repetição devolve o recurso já criado. */
@Getter
@NoArgsConstructor
@Entity
@IdClass(ChaveIdempotencia.Pk.class)
@Table(name = "chave_idempotencia")
public class ChaveIdempotencia {

    public record Pk(String chave, String usuarioId) implements Serializable {
        public Pk() {
            this(null, null);
        }
    }

    @Id
    private String chave;

    @Id
    @Column(name = "usuario_id")
    private String usuarioId;

    private String rota;

    @Column(name = "recurso_id")
    private String recursoId;

    @Column(name = "criado_em")
    private Instant criadoEm;

    public ChaveIdempotencia(String chave, String usuarioId, String rota, String recursoId) {
        this.chave = chave;
        this.usuarioId = usuarioId;
        this.rota = rota;
        this.recursoId = recursoId;
        this.criadoEm = Instant.now();
    }
}
