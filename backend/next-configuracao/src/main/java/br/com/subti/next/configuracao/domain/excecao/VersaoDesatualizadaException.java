package br.com.subti.next.configuracao.domain.excecao;

/** Controle otimista: o If-Match não corresponde à versão atual (status 412). */
public class VersaoDesatualizadaException extends NegocioException {

    public VersaoDesatualizadaException() {
        super("versao-desatualizada", "Registro alterado por outra pessoa",
                "O registro foi alterado depois que você o abriu. Recarregue a página e repita a alteração.");
    }

    @Override
    public int status() {
        return 412;
    }
}
