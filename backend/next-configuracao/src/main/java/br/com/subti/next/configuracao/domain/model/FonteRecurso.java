package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** N08 - Fonte de recursos que custeia contratos. */
@Getter
@Setter
@Entity
@Table(name = "fonte_recurso")
public class FonteRecurso extends CadastroComSituacao {

    private String codigo;
    private String nome;
    private String descricao;

    @Override
    public String tipoEntidade() {
        return "fonte-recurso";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("nome", nome);
        m.put("descricao", descricao);
        m.put("situacao", getSituacao().name());
        return m;
    }
}
