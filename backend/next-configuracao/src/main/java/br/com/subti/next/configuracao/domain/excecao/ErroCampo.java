package br.com.subti.next.configuracao.domain.excecao;

/** Erro associado a um campo de entrada (formato Problem Details, propriedade "errors"). */
public record ErroCampo(String field, String message) {
}
