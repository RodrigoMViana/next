package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/**
 * N03 - Acordo de nível de serviço da contratada (via contrato) ou da equipe interna (RN11).
 * As regras ficam nas versões (RN14).
 */
@Getter
@Setter
@Entity
@Table(name = "sla")
public class Sla extends CadastroComSituacao {

    public enum TipoResponsavel { CONTRATADA, EQUIPE_INTERNA }

    private String codigo;
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_responsavel")
    private TipoResponsavel tipoResponsavel;

    @ManyToOne
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    /** Cópia da contratada do contrato, para a autorização fina do N2 gestor. */
    @Column(name = "contratada_id")
    private UUID contratadaId;

    @Column(name = "equipe_interna")
    private String equipeInterna;

    @ManyToMany
    @JoinTable(name = "sla_servico", joinColumns = @JoinColumn(name = "sla_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new LinkedHashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sla_id", nullable = false)
    private List<SlaSistema> sistemas = new ArrayList<>();

    @Override
    public String tipoEntidade() {
        return "sla";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("nome", nome);
        m.put("tipoResponsavel", tipoResponsavel == null ? null : tipoResponsavel.name());
        m.put("contrato", contrato == null ? null : contrato.getNumero());
        m.put("equipeInterna", equipeInterna);
        m.put("servicos", servicos.stream().map(Servico::getCodigo).sorted().collect(Collectors.joining(", ")));
        m.put("sistemas", sistemas.stream().map(SlaSistema::getSistemaNome).sorted().collect(Collectors.joining(", ")));
        m.put("situacao", getSituacao().name());
        return m;
    }
}
