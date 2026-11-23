package br.com.subti.next.configuracao.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/** Cadastro com situação Ativo/Desativado e justificativa (RN04, RN08, RN15). */
@Getter
@MappedSuperclass
public abstract class CadastroComSituacao extends EntidadeBase implements Rastreavel {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Situacao situacao = Situacao.ATIVO;

    @Column(name = "justificativa_situacao")
    private String justificativaSituacao;

    public boolean ativo() {
        return situacao == Situacao.ATIVO;
    }

    public void desativar(String justificativa) {
        if (situacao == Situacao.DESATIVADO) {
            throw new IllegalStateException("O registro já está desativado.");
        }
        situacao = Situacao.DESATIVADO;
        justificativaSituacao = justificativa;
    }

    public void reativar(String justificativa) {
        if (situacao == Situacao.ATIVO) {
            throw new IllegalStateException("O registro já está ativo.");
        }
        situacao = Situacao.ATIVO;
        justificativaSituacao = justificativa;
    }
}
