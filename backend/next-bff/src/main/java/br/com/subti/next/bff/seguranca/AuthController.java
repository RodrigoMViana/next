package br.com.subti.next.bff.seguranca;

import br.com.subti.next.bff.web.ErroBff;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticação. Fase atual: login de desenvolvimento com usuários fictícios e MFA simulado para
 * usuários de fábrica. Integração pendente: OIDC Authorization Code + PKCE com gov.br e SSO do Next
 * (Arquitetura 8.1), substituindo /login por /oauth2/authorization/govbr.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record Login(@NotBlank String usuarioId, String codigoMfa) {
    }

    public record SessaoSaida(String id, String nome, String cpfMascarado, Set<String> perfis, String contratadaId,
                              boolean usuarioDeFabrica, boolean acessaConfiguracao, Map<String, Boolean> permissoes,
                              Instant loginEm, String modoAutenticacao) {
    }

    private final SecurityContextRepository repositorio;
    private final LimiteTaxa limite;
    private final String modo;
    private final String codigoMfa;
    private final int loginsPorMinuto;

    public AuthController(SecurityContextRepository repositorio, LimiteTaxa limite,
                          @Value("${next.auth.modo:dev}") String modo,
                          @Value("${next.auth.dev-mfa-codigo:123456}") String codigoMfa,
                          @Value("${next.seguranca.limites.login-por-minuto:10}") int loginsPorMinuto) {
        this.repositorio = repositorio;
        this.limite = limite;
        this.modo = modo;
        this.codigoMfa = codigoMfa;
        this.loginsPorMinuto = loginsPorMinuto;
    }

    /** Garante o cookie XSRF-TOKEN e devolve o nome do cabeçalho esperado. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/usuarios-demo")
    public List<Map<String, Object>> usuariosDemo() {
        exigirModoDev();
        return UsuariosDesenvolvimento.USUARIOS.stream().map(u -> Map.<String, Object>of("id", u.id(), "nome", u.nome(),
                "perfis", u.perfis(), "descricao", u.descricao(), "exigeMfa", u.fabrica())).toList();
    }

    @PostMapping("/login")
    public SessaoSaida login(@Valid @RequestBody Login e, HttpServletRequest req, HttpServletResponse res) {
        exigirModoDev();
        if (!limite.permitir("login:" + req.getRemoteAddr(), loginsPorMinuto)) {
            throw new ErroBff(429, "limite-de-tentativas", "Muitas tentativas", "Aguarde um minuto e tente novamente.");
        }
        var dev = UsuariosDesenvolvimento.buscar(e.usuarioId())
                .orElseThrow(() -> new ErroBff(401, "credenciais-invalidas", "Usuário não encontrado", "Usuário inexistente.",
                        "usuarioId"));
        if (dev.fabrica()) {
            if (e.codigoMfa() == null || e.codigoMfa().isBlank()) {
                throw new ErroBff(401, "mfa-obrigatorio", "MFA obrigatório",
                        "Usuários de fábrica precisam informar o código de autenticação multifator.", "codigoMfa");
            }
            if (!codigoMfa.equals(e.codigoMfa().trim())) {
                throw new ErroBff(401, "mfa-invalido", "Código MFA inválido", "O código informado não confere.", "codigoMfa");
            }
        }
        UsuarioSessao u = new UsuarioSessao(dev.id(), dev.nome(), dev.cpf(), dev.perfis(), dev.contratadaId(),
                dev.fabrica(), Instant.now());
        HttpSession antiga = req.getSession(false);
        if (antiga != null) {
            antiga.invalidate();
        }
        req.getSession(true);
        var auth = new UsernamePasswordAuthenticationToken(u, null,
                u.perfis().stream().map(p -> new SimpleGrantedAuthority("ROLE_" + p)).collect(Collectors.toSet()));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        repositorio.saveContext(ctx, req, res);
        return saida(u);
    }

    @GetMapping("/sessao")
    public ResponseEntity<SessaoSaida> sessao() {
        return Sessao.usuario().map(u -> ResponseEntity.ok(saida(u)))
                .orElseThrow(() -> new ErroBff(401, "nao-autenticado", "Sessão inexistente", "Entre para continuar."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private SessaoSaida saida(UsuarioSessao u) {
        boolean gestao = u.temAlgum(Rbac.GESTAO);
        Map<String, Boolean> permissoes = Map.of(
                "escreverCadastros", gestao,
                "lerCadastros", u.temAlgum(Rbac.LEITURA),
                "lerPortfolio", u.temAlgum(Rbac.LEITURA_PORTFOLIO),
                "notificacoes", gestao,
                "auditoria", gestao,
                "parecerSla", u.tem("N2_GESTOR"),
                "simularEventos", u.tem("ADMINISTRADOR"));
        return new SessaoSaida(u.id(), u.nome(), u.cpfMascarado(), u.perfis(), u.contratadaId(), u.usuarioDeFabrica(),
                Rbac.acessaModulo(u), permissoes, u.loginEm(), modo);
    }

    private void exigirModoDev() {
        if (!"dev".equals(modo)) {
            throw new ErroBff(404, "rota-inexistente", "Rota inexistente", "Login de desenvolvimento desabilitado.");
        }
    }
}
