package br.com.subti.next.bff.seguranca;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Autorização grossa (perfil × funcionalidade) do BFF para o módulo Configuração, conforme a proposta
 * de perfis da seção 5.4 do DOD. A autorização fina (perfil × dado) fica no módulo (RNF02).
 * A primeira regra que casar com método e caminho decide; o padrão é negar.
 */
public final class Rbac {

    public static final Set<String> GESTAO = Set.of("ADMINISTRADOR", "GESTOR_TI");
    public static final Set<String> LEITURA = Set.of("ADMINISTRADOR", "GESTOR_TI", "N1", "N2_GESTOR");
    public static final Set<String> LEITURA_PORTFOLIO = Set.of("ADMINISTRADOR", "GESTOR_TI", "N1");

    record Regra(String metodos, Pattern caminho, Set<String> perfis) {
        boolean casa(String metodo, String path) {
            return (metodos.equals("*") || metodos.contains(metodo)) && caminho.matcher(path).matches();
        }
    }

    private static final String ID = "[0-9a-fA-F-]{36}";

    /** Caminhos relativos a /api/v1/configuracao. */
    private static final List<Regra> REGRAS = List.of(
            // Parecer da contratada: somente N2 gestor (a fábrica é conferida no módulo)
            new Regra("POST", Pattern.compile("/slas/" + ID + "/versoes/" + ID + "/parecer"), Set.of("N2_GESTOR")),
            // Simulações são cálculos sem gravação: perfis de leitura
            new Regra("POST", Pattern.compile("/(slas|calendarios)/" + ID + "/simulacoes"), LEITURA),
            // Portfólio (N04): leitura por Administrador, Gestor de TI e N1; vínculo só gestão
            new Regra("GET", Pattern.compile("/portfolio/.*"), LEITURA_PORTFOLIO),
            // Notificações (N06), auditoria (N05) e usuários do Core: somente gestão
            new Regra("*", Pattern.compile("/notificacoes(/.*)?"), GESTAO),
            new Regra("GET", Pattern.compile("/auditoria/.*"), GESTAO),
            new Regra("GET", Pattern.compile("/usuarios"), GESTAO),
            // Simulação de eventos de outros módulos (ferramenta de homologação)
            new Regra("POST", Pattern.compile("/eventos"), Set.of("ADMINISTRADOR")),
            // Cadastros N01-N03, N07-N10: leitura ampla, escrita só gestão
            new Regra("GET", Pattern.compile("/(painel|contratos|contratadas|slas|servicos|unidades-medida|fontes-recurso|calendarios)(/.*)?"),
                    LEITURA),
            new Regra("POST,PUT,PATCH,DELETE",
                    Pattern.compile("/(contratos|contratadas|slas|servicos|unidades-medida|fontes-recurso|calendarios)(/.*)?"),
                    GESTAO));

    private Rbac() {
    }

    /** Perfis autorizados para a rota, ou vazio se nenhuma regra casar (negado). */
    public static Set<String> perfisPermitidos(String metodo, String caminho) {
        return REGRAS.stream().filter(r -> r.casa(metodo, caminho)).findFirst().map(Regra::perfis).orElse(Set.of());
    }

    public static boolean permitido(UsuarioSessao u, String metodo, String caminho) {
        return u.temAlgum(perfisPermitidos(metodo, caminho));
    }

    /** Acesso ao módulo Configuração (menu): perfis citados na seção 5.4 do DOD. */
    public static boolean acessaModulo(UsuarioSessao u) {
        return u.temAlgum(LEITURA);
    }
}
