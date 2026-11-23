package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.ContratoCalculos;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/**
 * N01 - Contrato de desenvolvimento, manutenção ou sustentação.
 * O número é um código de negócio único, separado do UUID (RN03).
 */
@Getter
@Setter
@Entity
@Table(name = "contrato")
public class Contrato extends CadastroComSituacao {

    private String numero;
    private String objeto;

    @ManyToOne(optional = false)
    @JoinColumn(name = "contratada_id")
    private Contratada contratada;

    @ManyToOne(optional = false)
    @JoinColumn(name = "calendario_id")
    private Calendario calendario;

    @Column(name = "vigencia_inicio")
    private LocalDate vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDate vigenciaFim;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "contrato_id", nullable = false)
    private List<ContratoItem> itens = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "contrato_fonte", joinColumns = @JoinColumn(name = "contrato_id"),
            inverseJoinColumns = @JoinColumn(name = "fonte_recurso_id"))
    private Set<FonteRecurso> fontes = new LinkedHashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "contrato_id", nullable = false)
    private List<ContratoSistema> sistemas = new ArrayList<>();

    @OneToMany
    @JoinColumn(name = "contrato_id", insertable = false, updatable = false)
    private List<ContratoAditivo> aditivos = new ArrayList<>();

    @OneToMany
    @JoinColumn(name = "contrato_id", insertable = false, updatable = false)
    private List<ContratoFiscal> fiscais = new ArrayList<>();

    private List<ContratoCalculos.Aditivo> aditivosCalculo() {
        return aditivos.stream().map(ContratoAditivo::paraCalculo).toList();
    }

    /** Valor original (soma dos itens, RN02). */
    public BigDecimal valorOriginal() {
        return ContratoCalculos.valorOriginal(itens.stream()
                .map(i -> new ContratoCalculos.Item(i.getVolume(), i.getValorUnitario())).toList());
    }

    /** Valor efetivo (itens + aditivos de valor, RN02/RN05). */
    public BigDecimal valorEfetivo() {
        return ContratoCalculos.valorEfetivo(itens.stream()
                .map(i -> new ContratoCalculos.Item(i.getVolume(), i.getValorUnitario())).toList(), aditivosCalculo());
    }

    /** Vigência efetiva (RN05). */
    public LocalDate vigenciaFimEfetiva() {
        return ContratoCalculos.vigenciaFimEfetiva(vigenciaFim, aditivosCalculo());
    }

    /** RN01: contrato ativo e dentro da vigência efetiva na data. */
    public boolean utilizavelEm(LocalDate data) {
        return ativo() && ContratoCalculos.vigenteEm(data, vigenciaInicio, vigenciaFimEfetiva());
    }

    public List<ContratoFiscal> fiscaisVigentes(LocalDate data) {
        return fiscais.stream().filter(f -> f.vigenteEm(data)).toList();
    }

    @Override
    public String tipoEntidade() {
        return "contrato";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("numero", numero);
        m.put("objeto", objeto);
        m.put("contratada", contratada == null ? null : contratada.getRazaoSocial());
        m.put("calendario", calendario == null ? null : calendario.getNome());
        m.put("vigenciaInicio", String.valueOf(vigenciaInicio));
        m.put("vigenciaFim", String.valueOf(vigenciaFim));
        m.put("itens", itens.stream().map(i -> i.getServico().getCodigo() + ": " + i.getVolume().toPlainString()
                + " x " + i.getValorUnitario().toPlainString()).collect(Collectors.joining("; ")));
        m.put("fontes", fontes.stream().map(FonteRecurso::getCodigo).sorted().collect(Collectors.joining(", ")));
        m.put("sistemas", sistemas.stream().map(ContratoSistema::getSistemaNome).sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", ")));
        m.put("situacao", getSituacao().name());
        return m;
    }
}
