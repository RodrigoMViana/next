package br.com.subti.next.configuracao.api;

import static br.com.subti.next.configuracao.api.Respostas.IF_MATCH;

import br.com.subti.next.configuracao.application.GovernancaService;
import br.com.subti.next.configuracao.application.HistoricoService;
import br.com.subti.next.configuracao.application.NotificacaoService;
import br.com.subti.next.configuracao.application.Pagina;
import br.com.subti.next.configuracao.application.PortfolioService;
import br.com.subti.next.configuracao.application.Suporte;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.MudancaSituacao;
import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.EventoCatalogo;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.NotificacaoEntrada;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.NotificacaoSaida;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.PreVisualizacaoEntrada;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.PreVisualizacaoSaida;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.EventoEntrada;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.EventoResultado;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.Painel;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.Sistemas;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.porta.CoreGateway;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API interna: notificações (N06), portfólio (N04), governança (N05), painel e usuários do Core. */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Geral", description = "N04, N05, N06, painel e eventos")
public class GeralController {

    private final NotificacaoService notificacoes;
    private final PortfolioService portfolio;
    private final GovernancaService governanca;
    private final HistoricoService historico;

    public GeralController(NotificacaoService notificacoes, PortfolioService portfolio, GovernancaService governanca,
                           HistoricoService historico) {
        this.notificacoes = notificacoes;
        this.portfolio = portfolio;
        this.governanca = governanca;
        this.historico = historico;
    }

    // ---- Notificações (N06)
    @GetMapping("/notificacoes/eventos")
    public List<EventoCatalogo> catalogo() {
        return notificacoes.catalogo();
    }

    @GetMapping("/notificacoes")
    public Pagina<NotificacaoSaida> listarNotificacoes(@RequestParam(required = false) String termo,
                                                       @RequestParam(required = false) Situacao situacao,
                                                       @RequestParam(required = false) String evento,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size,
                                                       @RequestParam(required = false) String sort) {
        return notificacoes.listar(termo, situacao, evento, page, size, sort);
    }

    @GetMapping("/notificacoes/{id}")
    public ResponseEntity<NotificacaoSaida> obterNotificacao(@PathVariable UUID id) {
        NotificacaoSaida s = notificacoes.obter(id);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/notificacoes")
    public ResponseEntity<NotificacaoSaida> criarNotificacao(@Valid @RequestBody NotificacaoEntrada e) {
        NotificacaoSaida s = notificacoes.criar(e);
        return Respostas.criado("/internal/v1/notificacoes", s.id(), s, s.version());
    }

    @PutMapping("/notificacoes/{id}")
    public ResponseEntity<NotificacaoSaida> alterarNotificacao(@PathVariable UUID id,
                                                               @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                               @Valid @RequestBody NotificacaoEntrada e) {
        NotificacaoSaida s = notificacoes.alterar(id, ifMatch, e);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/notificacoes/{id}/desativacao")
    public NotificacaoSaida desativarNotificacao(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return notificacoes.mudarSituacao(id, false, e.justificativa());
    }

    @PostMapping("/notificacoes/{id}/reativacao")
    public NotificacaoSaida reativarNotificacao(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return notificacoes.mudarSituacao(id, true, e.justificativa());
    }

    @PostMapping("/notificacoes/pre-visualizacao")
    public PreVisualizacaoSaida preVisualizar(@Valid @RequestBody PreVisualizacaoEntrada e) {
        return notificacoes.preVisualizar(e);
    }

    @GetMapping("/notificacoes/{id}/historico")
    public List<HistoricoDto> historicoNotificacao(@PathVariable UUID id) {
        notificacoes.obter(id);
        return historico.listar("notificacao", id);
    }

    // ---- Portfólio (N04)
    @GetMapping("/portfolio/sistemas")
    public Sistemas sistemas(@RequestParam(required = false) String termo,
                             @RequestParam(defaultValue = "false") boolean somentePendentes) {
        return portfolio.listar(termo, somentePendentes);
    }

    /** Consumo de eventos de outros módulos (fase 1: chamado pelo barramento/BFF; fase 2: Redis Streams). */
    @PostMapping("/eventos")
    public EventoResultado evento(@Valid @RequestBody EventoEntrada e) {
        Suporte.exigirPerfil(Suporte.ADMINISTRADOR);
        return portfolio.consumir(e);
    }

    // ---- Governança (N05), painel e usuários
    @GetMapping("/auditoria/governanca")
    public Pagina<HistoricoDto> governanca(@RequestParam(required = false) String usuarioId,
                                           @RequestParam(required = false) String entidade,
                                           @RequestParam(required = false) String entidadeId,
                                           @RequestParam(required = false) String acao,
                                           @RequestParam(required = false) Instant de,
                                           @RequestParam(required = false) Instant ate,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String sort) {
        return governanca.pesquisar(usuarioId, entidade, entidadeId, acao, de, ate, page, size, sort);
    }

    @GetMapping("/painel")
    public Painel painel() {
        return governanca.painel();
    }

    @GetMapping("/usuarios")
    public List<CoreGateway.UsuarioCore> usuarios(@RequestParam(required = false) String termo) {
        return governanca.usuarios(termo);
    }
}
