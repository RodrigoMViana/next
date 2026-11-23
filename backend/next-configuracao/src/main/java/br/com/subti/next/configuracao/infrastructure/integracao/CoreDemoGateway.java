package br.com.subti.next.configuracao.infrastructure.integracao;

import static br.com.subti.next.configuracao.infrastructure.integracao.DadosFicticios.FABRICA_ALFA;
import static br.com.subti.next.configuracao.infrastructure.integracao.DadosFicticios.FABRICA_BETA;

import br.com.subti.next.configuracao.domain.porta.CoreGateway;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de demonstração do next-core (usuários fictícios). Substituir pelo cliente HTTP do Core
 * quando o módulo estiver disponível (ver docs/02-integracoes-pendentes.md).
 */
@Component
@ConditionalOnProperty(name = "next.integracao.core.modo", havingValue = "demo", matchIfMissing = true)
public class CoreDemoGateway implements CoreGateway {

    static final List<UsuarioCore> USUARIOS = List.of(
            new UsuarioCore(DadosFicticios.U_ADMIN, "Ana Administradora", Set.of("ADMINISTRADOR"), null, true),
            new UsuarioCore(DadosFicticios.U_GESTOR_TI, "Gustavo Gestor de TI", Set.of("GESTOR_TI"), null, true),
            new UsuarioCore(DadosFicticios.U_N1, "Nina Atendente N1", Set.of("N1"), null, false),
            new UsuarioCore(DadosFicticios.U_N2_ALFA, "Fábio Gestor (Fábrica Alfa)", Set.of("N2_GESTOR"), FABRICA_ALFA, true),
            new UsuarioCore(DadosFicticios.U_TEC_ALFA, "Tânia Técnica (Fábrica Alfa)", Set.of("N2_TECNICO"), FABRICA_ALFA, true),
            new UsuarioCore(DadosFicticios.U_N2_BETA, "Bruno Gestor (Fábrica Beta)", Set.of("N2_GESTOR"), FABRICA_BETA, true),
            new UsuarioCore(DadosFicticios.U_FISCAL_TEC, "Carla Fiscal Técnica", Set.of("FACILITADOR"), null, false),
            new UsuarioCore(DadosFicticios.U_FISCAL_ADM, "Rui Fiscal Administrativo", Set.of("FACILITADOR"), null, false),
            new UsuarioCore(DadosFicticios.U_FISCAL_REQ, "Sofia Fiscal Requisitante", Set.of("RESPONSAVEL_AREA"), null, false),
            new UsuarioCore(DadosFicticios.U_CLIENTE, "Caio Cliente", Set.of("CLIENTE"), null, false));

    @Override
    public List<UsuarioCore> pesquisar(String termo) {
        if (termo == null || termo.isBlank()) {
            return USUARIOS;
        }
        String t = normalizar(termo);
        return USUARIOS.stream().filter(u -> normalizar(u.nomeExibicao()).contains(t)).toList();
    }

    @Override
    public Optional<UsuarioCore> buscar(String id) {
        return USUARIOS.stream().filter(u -> u.id().equals(id)).findFirst();
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
