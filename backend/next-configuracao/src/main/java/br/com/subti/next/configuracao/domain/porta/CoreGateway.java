package br.com.subti.next.configuracao.domain.porta;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Porta para o next-core (usuários e perfis). RN10: o módulo só recebe o identificador e o nome de
 * exibição; CPF, e-mail e telefone ficam no Core.
 */
public interface CoreGateway {

    record UsuarioCore(String id, String nomeExibicao, Set<String> perfis, UUID contratadaId, boolean mfaAtivo) {
    }

    List<UsuarioCore> pesquisar(String termo);

    Optional<UsuarioCore> buscar(String id);
}
