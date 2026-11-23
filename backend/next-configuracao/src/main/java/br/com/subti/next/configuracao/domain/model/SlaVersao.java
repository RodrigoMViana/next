package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.SlaCicloVida.SituacaoVersao;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/**
 * Versão do SLA com vigência própria (RN14). Chamados já abertos mantêm a versão vigente na abertura.
 * Situações: Rascunho → Em avaliação (parecer da contratada) → Vigente → Encerrada (RF17).
 */
@Getter
@Setter
@Entity
@Table(name = "sla_versao")
public class SlaVersao extends EntidadeBase implements Rastreavel {

    @Column(name = "sla_id")
    private UUID slaId;

    private int numero;

    @Enumerated(EnumType.STRING)
    private SituacaoVersao situacao = SituacaoVersao.RASCUNHO;

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @Column(name = "motivo_alteracao")
    private String motivoAlteracao;

    @Column(name = "parecer_favoravel")
    private Boolean parecerFavoravel;

    @Column(name = "parecer_texto")
    private String parecerTexto;

    @Column(name = "parecer_por_id")
    private String parecerPorId;

    @Column(name = "parecer_por_nome")
    private String parecerPorNome;

    @Column(name = "parecer_em")
    private Instant parecerEm;

    @Column(name = "decisao_observacao")
    private String decisaoObservacao;

    @Column(name = "decisao_por_id")
    private String decisaoPorId;

    @Column(name = "decisao_por_nome")
    private String decisaoPorNome;

    @Column(name = "decisao_em")
    private Instant decisaoEm;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sla_versao_id", nullable = false)
    @OrderBy("ordem")
    private List<SlaRegra> regras = new ArrayList<>();

    @Override
    public String tipoEntidade() {
        return "sla-versao";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("numero", String.valueOf(numero));
        m.put("situacao", situacao.name());
        m.put("vigenciaInicio", vigenciaInicio == null ? null : vigenciaInicio.toString());
        m.put("vigenciaFim", vigenciaFim == null ? null : vigenciaFim.toString());
        m.put("parecer", parecerFavoravel == null ? null : (parecerFavoravel ? "Favorável" : "Desfavorável")
                + (parecerTexto == null ? "" : ": " + parecerTexto));
        m.put("regras", regras.stream().map(SlaRegra::resumo).collect(Collectors.joining("; ")));
        return m;
    }
}
