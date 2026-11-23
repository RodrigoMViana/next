package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.Cnpj;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** N02 - Empresa contratada (fábrica). CNPJ numérico ou alfanumérico, sem máscara (RN07, RN25 a RN28). */
@Getter
@Setter
@Entity
@Table(name = "contratada")
public class Contratada extends CadastroComSituacao {

    private String cnpj;

    @Column(name = "razao_social")
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Override
    public String tipoEntidade() {
        return "contratada";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("cnpj", Cnpj.formatar(cnpj));
        m.put("razaoSocial", razaoSocial);
        m.put("nomeFantasia", nomeFantasia);
        m.put("situacao", getSituacao().name());
        return m;
    }
}
