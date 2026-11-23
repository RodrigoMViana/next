package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/** N06 - Configuração de notificação: evento, canais, perfis, modelo e limiar (RN21, RN22). */
@Getter
@Setter
@Entity
@Table(name = "notificacao_config")
public class NotificacaoConfig extends CadastroComSituacao {

    private String nome;
    private String evento;

    @Column(name = "canal_app")
    private boolean canalApp;

    @Column(name = "canal_email")
    private boolean canalEmail;

    /** Perfis destinatários separados por vírgula. */
    private String perfis;

    private String assunto;
    private String modelo;

    @Column(name = "limiar_percentual")
    private Integer limiarPercentual;

    public Set<String> perfisSet() {
        if (perfis == null || perfis.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(perfis.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void definirPerfis(Set<String> lista) {
        this.perfis = String.join(",", lista);
    }

    @Override
    public String tipoEntidade() {
        return "notificacao";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("evento", evento);
        m.put("canais", (canalApp ? "Ferramenta " : "") + (canalEmail ? "E-mail" : ""));
        m.put("perfis", perfis);
        m.put("assunto", assunto);
        m.put("modelo", modelo);
        m.put("limiarPercentual", limiarPercentual == null ? null : limiarPercentual + "%");
        m.put("situacao", getSituacao().name());
        return m;
    }
}
