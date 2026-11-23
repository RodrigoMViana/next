package br.com.subti.next.configuracao.domain.excecao;

/** Perfil sem permissão sobre o dado (autorização fina, status 403). */
public class AcessoNegadoException extends NegocioException {

    public AcessoNegadoException(String detalhe) {
        super("acesso-negado", "Acesso negado", detalhe);
    }

    @Override
    public int status() {
        return 403;
    }
}
