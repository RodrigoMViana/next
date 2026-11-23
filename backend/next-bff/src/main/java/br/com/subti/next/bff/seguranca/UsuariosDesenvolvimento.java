package br.com.subti.next.bff.seguranca;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Usuários fictícios do login de desenvolvimento (substitui o gov.br até o credenciamento — integração
 * pendente). Os identificadores coincidem com o adaptador de demonstração do Core no módulo.
 * CPFs fictícios, gerados apenas para teste (Premissa 8).
 */
public final class UsuariosDesenvolvimento {

    public record UsuarioDev(String id, String nome, String cpf, Set<String> perfis, String contratadaId,
                             String descricao) {
        public boolean fabrica() {
            return perfis.contains("N2_GESTOR") || perfis.contains("N2_TECNICO");
        }
    }

    public static final String FABRICA_ALFA = "a1fa0000-0000-4000-8000-000000000001";
    public static final String FABRICA_BETA = "be7a0000-0000-4000-8000-000000000002";

    public static final List<UsuarioDev> USUARIOS = List.of(
            new UsuarioDev("10000000-0000-4000-8000-000000000001", "Ana Administradora", "52998224725",
                    Set.of("ADMINISTRADOR"), null, "Administrador: parâmetros e todas as telas"),
            new UsuarioDev("10000000-0000-4000-8000-000000000002", "Gustavo Gestor de TI", "11144477735",
                    Set.of("GESTOR_TI"), null, "Gestor de TI: cadastra, decide vigência de SLA e consulta auditoria"),
            new UsuarioDev("10000000-0000-4000-8000-000000000003", "Nina Atendente N1", "39053344705",
                    Set.of("N1"), null, "N1: leitura dos cadastros e do Portfólio"),
            new UsuarioDev("10000000-0000-4000-8000-000000000004", "Fábio Gestor (Fábrica Alfa)", "71428793860",
                    Set.of("N2_GESTOR"), FABRICA_ALFA, "N2 gestor: vê só a Fábrica Alfa e dá parecer no SLA (MFA)"),
            new UsuarioDev("10000000-0000-4000-8000-000000000006", "Bruno Gestor (Fábrica Beta)", "48527370000",
                    Set.of("N2_GESTOR"), FABRICA_BETA, "N2 gestor: vê só a Fábrica Beta (MFA)"),
            new UsuarioDev("10000000-0000-4000-8000-000000000005", "Tânia Técnica (Fábrica Alfa)", "86288366757",
                    Set.of("N2_TECNICO"), FABRICA_ALFA, "N2+ técnico: sem acesso a este módulo (MFA)"),
            new UsuarioDev("10000000-0000-4000-8000-000000000009", "Caio Cliente", "15350946056",
                    Set.of("CLIENTE"), null, "Cliente/solicitante: sem acesso a este módulo"));

    private UsuariosDesenvolvimento() {
    }

    public static Optional<UsuarioDev> buscar(String id) {
        return USUARIOS.stream().filter(u -> u.id().equals(id)).findFirst();
    }
}
