package br.com.subti.next.configuracao.domain.excecao;

/** Recurso inexistente ou fora do escopo de dados do usuário (status 404). */
public class RecursoNaoEncontradoException extends NegocioException {

    public RecursoNaoEncontradoException(String recurso, Object id) {
        super("recurso-nao-encontrado", "Registro não encontrado",
                recurso + " " + id + " não existe ou não está disponível para o seu perfil.");
    }

    @Override
    public int status() {
        return 404;
    }
}
