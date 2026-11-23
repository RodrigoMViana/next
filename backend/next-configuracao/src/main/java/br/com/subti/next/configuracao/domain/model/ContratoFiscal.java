package br.com.subti.next.configuracao.domain.model;

import br.com.subti.next.configuracao.domain.regra.Fiscalizacao;
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

/** Designação de fiscal do contrato com tipo e período (RF04, RN06). */
@Getter
@Setter
@Entity
@Table(name = "contrato_fiscal")
public class ContratoFiscal extends EntidadeBase implements Rastreavel {

    public enum TipoFiscal { GESTOR_CONTRATO, FISCAL_TECNICO, FISCAL_ADMINISTRATIVO, FISCAL_REQUISITANTE }

    @Column(name = "contrato_id")
    private UUID contratoId;

    @Column(name = "usuario_id")
    private String usuarioId;

    @Column(name = "nome_exibicao")
    private String nomeExibicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_fiscal")
    private TipoFiscal tipoFiscal;

    private LocalDate inicio;
    private LocalDate fim;

    @Column(name = "motivo_encerramento")
    private String motivoEncerramento;

    @Column(name = "substituido_por_id")
    private UUID substituidoPorId;

    public boolean vigenteEm(LocalDate data) {
        return new Fiscalizacao.Designacao(inicio, fim).vigenteEm(data);
    }

    @Override
    public String tipoEntidade() {
        return "contrato-fiscal";
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("fiscal", nomeExibicao);
        m.put("tipoFiscal", tipoFiscal == null ? null : tipoFiscal.name());
        m.put("inicio", String.valueOf(inicio));
        m.put("fim", fim == null ? null : fim.toString());
        m.put("motivoEncerramento", motivoEncerramento);
        return m;
    }
}
