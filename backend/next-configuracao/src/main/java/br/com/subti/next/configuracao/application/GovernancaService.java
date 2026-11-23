package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.exigirLeitura;
import static br.com.subti.next.configuracao.application.Suporte.hoje;
import static br.com.subti.next.configuracao.application.Suporte.texto;

import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.Painel;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.Pendencia;
import br.com.subti.next.configuracao.domain.model.Contrato;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.porta.CoreGateway;
import br.com.subti.next.configuracao.domain.porta.PortfolioGateway;
import br.com.subti.next.configuracao.domain.regra.SlaCicloVida.SituacaoVersao;
import br.com.subti.next.configuracao.domain.repositorio.CalendarioRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratadaRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratoRepository;
import br.com.subti.next.configuracao.domain.repositorio.EventoOutboxRepository;
import br.com.subti.next.configuracao.domain.repositorio.FonteRecursoRepository;
import br.com.subti.next.configuracao.domain.repositorio.HistoricoAlteracaoRepository;
import br.com.subti.next.configuracao.domain.repositorio.NotificacaoConfigRepository;
import br.com.subti.next.configuracao.domain.repositorio.ServicoRepository;
import br.com.subti.next.configuracao.domain.repositorio.SlaRepository;
import br.com.subti.next.configuracao.domain.repositorio.SlaVersaoRepository;
import br.com.subti.next.configuracao.domain.repositorio.UnidadeMedidaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de registros de governança (N05, RF11), painel de pendências e usuários do Core. */
@Service
@Transactional(readOnly = true)
public class GovernancaService {

    private static final Map<String, String> ORDEM = Map.of("ocorridoEm", "ocorridoEm", "entidade", "entidade",
            "usuarioNome", "usuarioNome", "acao", "acao");

    private final HistoricoAlteracaoRepository historicos;
    private final ContratoRepository contratos;
    private final ContratadaRepository contratadas;
    private final SlaRepository slas;
    private final SlaVersaoRepository versoes;
    private final UnidadeMedidaRepository unidades;
    private final FonteRecursoRepository fontes;
    private final ServicoRepository servicos;
    private final CalendarioRepository calendarios;
    private final NotificacaoConfigRepository notificacoes;
    private final EventoOutboxRepository outbox;
    private final PortfolioService portfolioService;
    private final PortfolioGateway portfolio;
    private final CoreGateway core;

    public GovernancaService(HistoricoAlteracaoRepository historicos, ContratoRepository contratos,
                             ContratadaRepository contratadas, SlaRepository slas, SlaVersaoRepository versoes,
                             UnidadeMedidaRepository unidades, FonteRecursoRepository fontes, ServicoRepository servicos,
                             CalendarioRepository calendarios, NotificacaoConfigRepository notificacoes,
                             EventoOutboxRepository outbox, PortfolioService portfolioService,
                             PortfolioGateway portfolio, CoreGateway core) {
        this.historicos = historicos;
        this.contratos = contratos;
        this.contratadas = contratadas;
        this.slas = slas;
        this.versoes = versoes;
        this.unidades = unidades;
        this.fontes = fontes;
        this.servicos = servicos;
        this.calendarios = calendarios;
        this.notificacoes = notificacoes;
        this.outbox = outbox;
        this.portfolioService = portfolioService;
        this.portfolio = portfolio;
        this.core = core;
    }

    /** RF11: filtros por usuário, recurso (entidade), ação e período. Somente Administrador e Gestor de TI. */
    public Pagina<HistoricoDto> pesquisar(String usuarioId, String entidade, String entidadeId, String acao, Instant de,
                                          Instant ate, Integer page, Integer size, String sort) {
        exigirGestao();
        return Pagina.de(historicos.pesquisar(texto(usuarioId), texto(entidade), texto(entidadeId), texto(acao), de, ate,
                Pagina.pageable(page, size, sort == null ? "ocorridoEm,desc" : sort, ORDEM, "ocorridoEm")), HistoricoDto::de);
    }

    /** Painel inicial do módulo: totais e pendências de configuração. */
    public Painel painel() {
        exigirLeitura();
        UsuarioAtual u = Suporte.usuario();
        LocalDate hoje = hoje();
        Map<String, Long> totais = new LinkedHashMap<>();
        List<Pendencia> pendencias = new ArrayList<>();
        List<Contrato> ativos = contratos.findBySituacao(Situacao.ATIVO).stream()
                .filter(c -> !u.restritoAFabrica() || c.getContratada().getId().equals(u.contratadaId())).toList();
        totais.put("contratosAtivos", (long) ativos.size());
        totais.put("contratosVigentes", ativos.stream().filter(c -> c.utilizavelEm(hoje)).count());
        if (!u.restritoAFabrica()) {
            totais.put("contratadas", contratadas.count());
            totais.put("slas", slas.count());
            totais.put("servicos", servicos.count());
            totais.put("unidadesMedida", unidades.count());
            totais.put("fontesRecurso", fontes.count());
            totais.put("calendarios", calendarios.count());
            totais.put("notificacoes", notificacoes.count());
        }
        for (Contrato c : ativos) {
            if (c.utilizavelEm(hoje) && c.fiscaisVigentes(hoje).isEmpty()) {
                pendencias.add(new Pendencia("CONTRATO_SEM_FISCAL", "Contrato " + c.getNumero()
                        + " sem fiscal com designação vigente (RN06).", "contrato", c.getId()));
            }
            LocalDate fim = c.vigenciaFimEfetiva();
            if (!fim.isBefore(hoje) && !fim.isAfter(hoje.plusDays(60))) {
                pendencias.add(new Pendencia("CONTRATO_VENCENDO", "Contrato " + c.getNumero() + " termina em "
                        + fim + ".", "contrato", c.getId()));
            }
        }
        versoes.findBySituacao(SituacaoVersao.EM_AVALIACAO).forEach(v -> slas.findById(v.getSlaId())
                .filter(s -> !u.restritoAFabrica() || java.util.Objects.equals(u.contratadaId(), s.getContratadaId()))
                .ifPresent(s -> pendencias.add(new Pendencia("SLA_EM_AVALIACAO", "SLA " + s.getCodigo() + " versão "
                        + v.getNumero() + " aguardando " + (v.getParecerFavoravel() == null ? "parecer da contratada" : "decisão de vigência")
                        + ".", "sla", s.getId()))));
        long semSla = 0;
        if (!u.restritoAFabrica()) {
            semSla = portfolioService.contarPendencias();
            if (semSla > 0) {
                pendencias.add(new Pendencia("SISTEMA_SEM_SLA", semSla + " sistema(s) ativo(s) no Portfólio sem SLA vigente (RN20).",
                        "portfolio", null));
            }
        }
        if (!u.restritoAFabrica()) {
            totais.put("sistemasSemSla", semSla);
        }
        return new Painel(totais, pendencias, outbox.countByPublicadoEmIsNull(), portfolio.disponivel(), hoje);
    }

    /** Usuários do Core para seleção de fiscais e membros de equipe (somente id e nome, RN10). */
    public List<CoreGateway.UsuarioCore> usuarios(String termo) {
        exigirGestao();
        return core.pesquisar(termo);
    }
}
