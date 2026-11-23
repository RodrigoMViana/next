package br.com.subti.next.configuracao.domain.excecao;

/** Edição enviada sem o cabeçalho If-Match (status 428). */
public class PreCondicaoObrigatoriaException extends NegocioException {

    public PreCondicaoObrigatoriaException() {
        super("if-match-obrigatorio", "Cabeçalho If-Match obrigatório",
                "Edições exigem o cabeçalho If-Match com a versão (ETag) do registro.");
    }

    @Override
    public int status() {
        return 428;
    }
}
