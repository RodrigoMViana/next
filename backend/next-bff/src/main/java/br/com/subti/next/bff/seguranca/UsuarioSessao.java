package br.com.subti.next.bff.seguranca;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * Usuário autenticado guardado na sessão do servidor. Nenhum token chega ao navegador (D47).
 * O CPF fica apenas aqui e é exibido mascarado (RNF05).
 */
public record UsuarioSessao(String id, String nome, String cpf, Set<String> perfis, String contratadaId,
                            boolean mfaValidado, Instant loginEm) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean tem(String perfil) {
        return perfis.contains(perfil);
    }

    public boolean temAlgum(Set<String> lista) {
        return perfis.stream().anyMatch(lista::contains);
    }

    /** CPF mascarado no formato ***.456.789-** (RN23). */
    public String cpfMascarado() {
        String d = cpf == null ? "" : cpf.replaceAll("\\D", "");
        return d.length() == 11 ? "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**" : "***.***.***-**";
    }

    /** Usuário de fábrica: MFA obrigatório (R4, RN09). */
    public boolean usuarioDeFabrica() {
        return perfis.contains("N2_GESTOR") || perfis.contains("N2_TECNICO");
    }
}
