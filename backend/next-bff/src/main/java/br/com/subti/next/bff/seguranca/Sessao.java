package br.com.subti.next.bff.seguranca;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuário da sessão corrente. */
public final class Sessao {

    private Sessao() {
    }

    public static Optional<UsuarioSessao> usuario() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioSessao u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }
}
