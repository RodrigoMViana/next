package br.com.subti.next.configuracao.domain.excecao;

import java.util.List;

/** Conflito de unicidade ou de estado (status 409). */
public class ConflitoException extends NegocioException {

    public ConflitoException(String codigo, String titulo, String detalhe) {
        super(codigo, titulo, detalhe);
    }

    public ConflitoException(String codigo, String titulo, String campo, String mensagem) {
        super(codigo, titulo, mensagem, List.of(new ErroCampo(campo, mensagem)));
    }

    @Override
    public int status() {
        return 409;
    }
}
