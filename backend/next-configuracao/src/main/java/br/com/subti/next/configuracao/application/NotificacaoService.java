package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.erroCampo;
import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.regra;
import static br.com.subti.next.configuracao.application.Suporte.termo;
import static br.com.subti.next.configuracao.application.Suporte.texto;
import static br.com.subti.next.configuracao.application.Suporte.verificarVersao;

import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.EventoCatalogo;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.NotificacaoEntrada;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.NotificacaoSaida;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.PreVisualizacaoEntrada;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos.PreVisualizacaoSaida;
import br.com.subti.next.configuracao.domain.excecao.RecursoNaoEncontradoException;
import br.com.subti.next.configuracao.domain.model.NotificacaoConfig;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.regra.RegraNotificacao;
import br.com.subti.next.configuracao.domain.repositorio.NotificacaoConfigRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuração de notificações (N06, RF13, RN21, RN22). */
@Service
@Transactional
public class NotificacaoService {

    /** Perfis da seção 8.2 da arquitetura (nenhum perfil novo é criado). */
    public static final Set<String> PERFIS = Set.of("ADMINISTRADOR", "GESTOR_TI", "FACILITADOR", "RESPONSAVEL_AREA",
            "MEMBRO_COMITE", "N1", "N2_GESTOR", "N2_TECNICO", "CLIENTE");

    /** Catálogo de eventos que podem ser notificados e as variáveis disponíveis para o modelo. */
    public static final List<EventoCatalogo> CATALOGO = List.of(
            new EventoCatalogo("configuracao.contrato.criado", "Configuração", "Contrato cadastrado",
                    List.of("contrato.numero", "contratada.razaoSocial", "contrato.vigenciaFim", "usuario.nome"), false),
            new EventoCatalogo("configuracao.contrato.vencendo", "Configuração", "Contrato próximo do fim da vigência",
                    List.of("contrato.numero", "contratada.razaoSocial", "contrato.vigenciaFim", "dias"), false),
            new EventoCatalogo("configuracao.aditivo.registrado", "Configuração", "Aditivo registrado em contrato",
                    List.of("contrato.numero", "aditivo.numero", "contrato.vigenciaFim", "contrato.valor"), false),
            new EventoCatalogo("configuracao.fiscal.substituido", "Configuração", "Fiscal do contrato substituído",
                    List.of("contrato.numero", "fiscal.anterior", "fiscal.novo", "data"), false),
            new EventoCatalogo("configuracao.sla.em-avaliacao", "Configuração", "Versão de SLA enviada para avaliação",
                    List.of("sla.codigo", "sla.nome", "sla.versao", "contrato.numero"), false),
            new EventoCatalogo("configuracao.sla.vigente", "Configuração", "Versão de SLA passou a vigorar",
                    List.of("sla.codigo", "sla.nome", "sla.versao", "sla.vigenciaInicio"), false),
            new EventoCatalogo("configuracao.sistema.pendente-configuracao", "Configuração",
                    "Sistema ativado no Portfólio sem SLA vigente", List.of("sistema.nome", "sistema.sigla"), false),
            new EventoCatalogo(RegraNotificacao.EVENTO_SLA_EM_RISCO, "Sustentação", "SLA de chamado em risco",
                    List.of("chamado.codigo", "sla.codigo", "sla.prazo", "sla.percentual", "fila.nome"), true),
            new EventoCatalogo("sustentacao.sla.violado", "Sustentação", "SLA de chamado violado",
                    List.of("chamado.codigo", "sla.codigo", "sla.prazo", "fila.nome"), false),
            new EventoCatalogo("portfolio.sistema.ativado", "Portfólio", "Sistema ativado no Portfólio",
                    List.of("sistema.nome", "sistema.sigla"), false));

    private static final Map<String, String> ORDEM = Map.of("nome", "nome", "evento", "evento", "situacao", "situacao");

    private final NotificacaoConfigRepository notificacoes;
    private final HistoricoService historico;

    public NotificacaoService(NotificacaoConfigRepository notificacoes, HistoricoService historico) {
        this.notificacoes = notificacoes;
        this.historico = historico;
    }

    public List<EventoCatalogo> catalogo() {
        exigirGestao();
        return CATALOGO;
    }

    @Transactional(readOnly = true)
    public Pagina<NotificacaoSaida> listar(String busca, Situacao situacao, String evento, Integer page, Integer size,
                                           String sort) {
        exigirGestao();
        return Pagina.de(notificacoes.pesquisar(termo(busca), situacao, texto(evento),
                Pagina.pageable(page, size, sort, ORDEM, "nome")), NotificacaoSaida::de);
    }

    @Transactional(readOnly = true)
    public NotificacaoSaida obter(UUID id) {
        exigirGestao();
        return NotificacaoSaida.de(notificacao(id));
    }

    public NotificacaoSaida criar(NotificacaoEntrada e) {
        exigirGestao();
        NotificacaoConfig n = new NotificacaoConfig();
        preencher(n, e);
        notificacoes.save(n);
        historico.criacao(n);
        return NotificacaoSaida.de(n);
    }

    public NotificacaoSaida alterar(UUID id, String ifMatch, NotificacaoEntrada e) {
        exigirGestao();
        NotificacaoConfig n = notificacao(id);
        verificarVersao(n, ifMatch);
        var antes = n.snapshot();
        preencher(n, e);
        notificacoes.saveAndFlush(n);
        historico.alteracao(n, antes, null);
        return NotificacaoSaida.de(n);
    }

    public NotificacaoSaida mudarSituacao(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        NotificacaoConfig n = notificacao(id);
        regra("situacao", () -> {
            if (ativar) {
                n.reativar(texto(justificativa));
            } else {
                n.desativar(texto(justificativa));
            }
        });
        notificacoes.saveAndFlush(n);
        historico.acao(n.tipoEntidade(), n.getId(), ativar ? HistoricoService.REATIVACAO : HistoricoService.DESATIVACAO,
                "Situação: " + n.getSituacao(), texto(justificativa));
        return NotificacaoSaida.de(n);
    }

    public PreVisualizacaoSaida preVisualizar(PreVisualizacaoEntrada e) {
        exigirGestao();
        EventoCatalogo ev = evento(e.evento());
        Map<String, String> exemplo = new LinkedHashMap<>();
        ev.variaveis().forEach(v -> exemplo.put(v, exemplo(v)));
        return new PreVisualizacaoSaida(RegraNotificacao.renderizar(e.assunto(), exemplo),
                RegraNotificacao.renderizar(e.modelo(), exemplo), exemplo);
    }

    private void preencher(NotificacaoConfig n, NotificacaoEntrada e) {
        if (!RegraNotificacao.eventoValido(e.evento())) {
            throw erroCampo("evento", "Use o padrão modulo.entidade.fato-no-passado, por exemplo sustentacao.sla.violado.");
        }
        EventoCatalogo ev = evento(e.evento());
        if (!e.canalApp() && !e.canalEmail()) {
            throw erroCampo("canalApp", "Escolha ao menos um canal: na ferramenta ou por e-mail.");
        }
        Set<String> perfis = e.perfis().stream().map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> invalidos = perfis.stream().filter(p -> !PERFIS.contains(p)).toList();
        if (!invalidos.isEmpty()) {
            throw erroCampo("perfis", "Perfis inexistentes: " + String.join(", ", invalidos) + ".");
        }
        regra("limiarPercentual", () -> RegraNotificacao.validarLimiar(e.evento(), e.limiarPercentual()));
        Set<String> permitidas = Set.copyOf(ev.variaveis());
        List<String> desconhecidas = RegraNotificacao.variaveisDesconhecidas(e.assunto() + " " + e.modelo(), permitidas);
        if (!desconhecidas.isEmpty()) {
            throw erroCampo("modelo", "Variáveis não disponíveis para este evento: " + String.join(", ", desconhecidas)
                    + ". Disponíveis: " + String.join(", ", ev.variaveis()) + ".");
        }
        if (e.canalEmail()) {
            List<String> proibidas = RegraNotificacao.variaveisProibidasEmEmail(e.assunto() + " " + e.modelo());
            if (!proibidas.isEmpty()) {
                throw erroCampo("modelo", "Mensagens por e-mail não podem exibir CPF nem conteúdo de chamados: "
                        + String.join(", ", proibidas) + ".");
            }
        }
        n.setNome(e.nome().trim());
        n.setEvento(e.evento());
        n.setCanalApp(e.canalApp());
        n.setCanalEmail(e.canalEmail());
        n.definirPerfis(perfis);
        n.setAssunto(e.assunto().trim());
        n.setModelo(e.modelo().trim());
        n.setLimiarPercentual(e.limiarPercentual());
    }

    private EventoCatalogo evento(String nome) {
        return CATALOGO.stream().filter(c -> c.evento().equals(nome)).findFirst()
                .orElseThrow(() -> erroCampo("evento", "Evento " + nome + " não está no catálogo de notificações."));
    }

    private static String exemplo(String variavel) {
        return switch (variavel) {
            case "contrato.numero" -> "CT-2026-0007";
            case "contratada.razaoSocial" -> "Fábrica Alfa Tecnologia Ltda.";
            case "contrato.vigenciaFim", "sla.vigenciaInicio", "data" -> "31/12/2027";
            case "contrato.valor" -> "R$ 1.234.567,89";
            case "dias" -> "30";
            case "aditivo.numero" -> "2º TA";
            case "fiscal.anterior" -> "Rui Fiscal";
            case "fiscal.novo" -> "Carla Fiscal";
            case "sla.codigo" -> "SLA-ALFA";
            case "sla.nome" -> "SLA de sustentação Alfa";
            case "sla.versao" -> "3";
            case "sla.prazo" -> "25/11/2026 14:30";
            case "sla.percentual" -> "80%";
            case "chamado.codigo" -> "SUP-2041";
            case "fila.nome" -> "N2 - Fábrica Alfa";
            case "sistema.nome" -> "Sistema de Arrecadação";
            case "sistema.sigla" -> "SIARR";
            case "usuario.nome" -> "Gustavo Gestor";
            default -> "[" + variavel + "]";
        };
    }

    private NotificacaoConfig notificacao(UUID id) {
        return notificacoes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Notificação", id));
    }
}
