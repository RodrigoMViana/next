package br.com.subti.next.configuracao.infrastructure.integracao;

import br.com.subti.next.configuracao.domain.porta.PortfolioGateway;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de demonstração do Portfólio, com sistemas fictícios. A API real está em desenvolvimento
 * na SUBTI; ative o adaptador HTTP com next.integracao.portfolio.modo=http.
 */
@Component
@ConditionalOnProperty(name = "next.integracao.portfolio.modo", havingValue = "demo", matchIfMissing = true)
public class PortfolioDemoGateway implements PortfolioGateway {

    private static final List<SistemaPortfolio> SISTEMAS = List.of(
            new SistemaPortfolio(DadosFicticios.SIS_ARRECADACAO, "SIARR", "Sistema de Arrecadação", "ATIVO", "Receita"),
            new SistemaPortfolio(DadosFicticios.SIS_PROTOCOLO, "PROT", "Protocolo Eletrônico", "ATIVO", "Administração"),
            new SistemaPortfolio(DadosFicticios.SIS_PORTAL, "PCID", "Portal do Cidadão", "ATIVO", "Atendimento"),
            new SistemaPortfolio(DadosFicticios.SIS_FOLHA, "FOLHA", "Folha de Pagamento", "ATIVO", "Gestão de Pessoas"),
            new SistemaPortfolio(DadosFicticios.SIS_ESTOQUE, "ALMOX", "Controle de Almoxarifado", "ATIVO", "Logística"),
            new SistemaPortfolio(DadosFicticios.SIS_LEGADO, "SGL", "Sistema Legado de Guias", "DESCONTINUADO", "Receita"));

    @Override
    public List<SistemaPortfolio> listar(String termo) {
        if (termo == null || termo.isBlank()) {
            return SISTEMAS;
        }
        String t = normalizar(termo);
        return SISTEMAS.stream().filter(s -> normalizar(s.nome()).contains(t) || normalizar(s.sigla()).contains(t)).toList();
    }

    @Override
    public Optional<SistemaPortfolio> buscar(UUID id) {
        return SISTEMAS.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Override
    public boolean disponivel() {
        return true;
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
