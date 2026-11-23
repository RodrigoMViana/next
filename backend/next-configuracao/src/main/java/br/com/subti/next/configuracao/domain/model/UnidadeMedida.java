package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** N07 - Unidade de medida dos serviços (por exemplo, Ponto de Função e hora). RN16: o PF é apurado pela Engenharia. */
@Getter
@Setter
@Entity
@Table(name = "unidade_medida")
public class UnidadeMedida extends CadastroComSituacao {

    private String sigla;
    private String nome;
    private String descricao;

    @Override
    public String tipoEntidade() {
        return "unidade-medida";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sigla", sigla);
        m.put("nome", nome);
        m.put("descricao", descricao);
        m.put("situacao", getSituacao().name());
        return m;
    }
}
