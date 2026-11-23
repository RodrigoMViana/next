package br.com.subti.next.configuracao.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * Identidade propagada pelo BFF no token interno (Arquitetura 6.2). Mantida por requisição.
 *
 * @param id           identificador do usuário no Core
 * @param nome         nome de exibição (RN10: o módulo não guarda CPF, e-mail ou telefone)
 * @param perfis       perfis da seção 8.2 da arquitetura
 * @param contratadaId fábrica do usuário, para perfis N2 (autorização fina)
 */
public record UsuarioAtual(String id, String nome, Set<String> perfis, UUID contratadaId) {

    public static final String SISTEMA_ID = "00000000-0000-0000-0000-000000000000";
    private static final ThreadLocal<UsuarioAtual> ATUAL = new ThreadLocal<>();

    public static UsuarioAtual sistema() {
        return new UsuarioAtual(SISTEMA_ID, "Sistema", Set.of("ADMINISTRADOR"), null);
    }

    public static UsuarioAtual get() {
        UsuarioAtual u = ATUAL.get();
        return u != null ? u : sistema();
    }

    public static void set(UsuarioAtual usuario) {
        ATUAL.set(usuario);
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public boolean tem(String perfil) {
        return perfis.contains(perfil);
    }

    /** N2 gestor sem perfil de gestão da TI: enxerga apenas a própria fábrica. */
    public boolean restritoAFabrica() {
        return tem("N2_GESTOR") && !tem("ADMINISTRADOR") && !tem("GESTOR_TI") && !tem("N1");
    }
}
