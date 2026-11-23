package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** N09 - Serviço monitorado por SLA, com tipo e unidade de medida (RN15). */
@Getter
@Setter
@Entity
@Table(name = "servico")
public class Servico extends CadastroComSituacao {

    /** RN15: todo serviço tem tipo. */
    public enum Tipo { DESENVOLVIMENTO, MANUTENCAO, SUSTENTACAO }

    private String codigo;
    private String nome;
    private String descricao;

    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "unidade_medida_id")
    private UnidadeMedida unidadeMedida;

    @Override
    public String tipoEntidade() {
        return "servico";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("nome", nome);
        m.put("descricao", descricao);
        m.put("tipo", tipo == null ? null : tipo.name());
        m.put("unidadeMedida", unidadeMedida == null ? null : unidadeMedida.getSigla());
        m.put("situacao", getSituacao().name());
        return m;
    }
}
