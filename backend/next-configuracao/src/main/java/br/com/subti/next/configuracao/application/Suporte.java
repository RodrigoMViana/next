package br.com.subti.next.configuracao.application;

import br.com.subti.next.configuracao.domain.excecao.AcessoNegadoException;
import br.com.subti.next.configuracao.domain.excecao.NegocioException;
import br.com.subti.next.configuracao.domain.excecao.PreCondicaoObrigatoriaException;
import br.com.subti.next.configuracao.domain.excecao.VersaoDesatualizadaException;
import br.com.subti.next.configuracao.domain.model.EntidadeBase;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/** Utilitários comuns aos casos de uso: permissões, concorrência, datas e conversão de erros. */
public final class Suporte {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String GESTOR_TI = "GESTOR_TI";
    public static final String N1 = "N1";
    public static final String N2_GESTOR = "N2_GESTOR";

    /** Fuso de referência para "hoje" nas regras de vigência. */
    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    private static Clock relogio = Clock.system(FUSO_PADRAO);

    private Suporte() {
    }

    public static LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /** Permite fixar a data em testes. */
    public static void usarRelogio(Clock clock) {
        relogio = clock;
    }

    public static UsuarioAtual usuario() {
        return UsuarioAtual.get();
    }

    /** Autorização em profundidade: o BFF já filtra por perfil, o módulo confere de novo. */
    public static void exigirPerfil(String... perfis) {
        UsuarioAtual u = usuario();
        if (Arrays.stream(perfis).noneMatch(u::tem)) {
            throw new AcessoNegadoException("Seu perfil não permite esta operação.");
        }
    }

    public static void exigirGestao() {
        exigirPerfil(ADMINISTRADOR, GESTOR_TI);
    }

    public static void exigirLeitura() {
        exigirPerfil(ADMINISTRADOR, GESTOR_TI, N1, N2_GESTOR);
    }

    /** RNF06: If-Match obrigatório e igual à versão atual. */
    public static void verificarVersao(EntidadeBase entidade, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreCondicaoObrigatoriaException();
        }
        String recebido = ifMatch.trim().replace("W/", "");
        if (!recebido.equals(entidade.etag()) && !recebido.equals(String.valueOf(entidade.versao()))) {
            throw new VersaoDesatualizadaException();
        }
    }

    /** Converte IllegalArgumentException/IllegalStateException de regras puras em erro de negócio por campo. */
    public static <T> T regra(String campo, Supplier<T> acao) {
        try {
            return acao.get();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw NegocioException.campo("regra-de-negocio", "Regra de negócio não atendida", campo, e.getMessage());
        }
    }

    public static void regra(String campo, Runnable acao) {
        regra(campo, () -> {
            acao.run();
            return null;
        });
    }

    public static NegocioException erroCampo(String campo, String mensagem) {
        return NegocioException.campo("validacao", "Dados inválidos", campo, mensagem);
    }

    /** Termo de busca para LIKE (minúsculas, com curingas) ou nulo. */
    public static String termo(String texto) {
        return texto == null || texto.isBlank() ? null : "%" + escaparLike(texto.trim().toLowerCase(Locale.ROOT)) + "%";
    }

    /** Escapa curingas digitados pelo usuário (o JPQL usa escape \). */
    public static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public static boolean contem(Set<String> conjunto, String valor) {
        return conjunto != null && conjunto.contains(valor);
    }
}
