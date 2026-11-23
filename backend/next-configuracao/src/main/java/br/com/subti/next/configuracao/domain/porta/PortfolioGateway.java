package br.com.subti.next.configuracao.domain.porta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta para o Portfólio de sistemas (N04). A API do Portfólio está em desenvolvimento na SUBTI;
 * a implementação HTTP é selecionada por configuração e há um adaptador de demonstração.
 */
public interface PortfolioGateway {

    /** Sistema do Portfólio, apenas com os atributos necessários para exibição (RN19). */
    record SistemaPortfolio(UUID id, String sigla, String nome, String situacao, String areaResponsavel) {
        public boolean ativo() {
            return "ATIVO".equalsIgnoreCase(situacao);
        }
    }

    List<SistemaPortfolio> listar(String termo);

    Optional<SistemaPortfolio> buscar(UUID id);

    /** Indica se o Portfólio respondeu (para degradação parcial na tela). */
    boolean disponivel();
}
