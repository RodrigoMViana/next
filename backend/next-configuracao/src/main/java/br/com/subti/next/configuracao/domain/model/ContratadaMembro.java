package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Membro da equipe da contratada: usuário do Core com papel e período (RF07, RN09, RN10).
 * MFA é obrigatório para todo usuário de fábrica e é exigido pelo Core/BFF no login.
 */
@Getter
@Setter
@Entity
@Table(name = "contratada_membro")
public class ContratadaMembro extends EntidadeBase implements Rastreavel {

    public enum Papel { N2_GESTOR, N2_TECNICO }

    @Column(name = "contratada_id")
    private UUID contratadaId;

    @Column(name = "usuario_id")
    private String usuarioId;

    @Column(name = "nome_exibicao")
    private String nomeExibicao;

    @Enumerated(EnumType.STRING)
    private Papel papel;

    private LocalDate inicio;
    private LocalDate fim;

    public boolean vigenteEm(LocalDate data) {
        return !data.isBefore(inicio) && (fim == null || !data.isAfter(fim));
    }

    @Override
    public String tipoEntidade() {
        return "contratada-membro";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("usuario", nomeExibicao);
        m.put("papel", papel == null ? null : papel.name());
        m.put("inicio", inicio == null ? null : inicio.toString());
        m.put("fim", fim == null ? null : fim.toString());
        return m;
    }
}
