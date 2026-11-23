package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.erroCampo;
import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.exigirPerfil;
import static br.com.subti.next.configuracao.application.Suporte.hoje;

import br.com.subti.next.configuracao.application.dto.PortfolioDtos.EventoEntrada;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.EventoResultado;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.SistemaSaida;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.Sistemas;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.Vinculo;
import br.com.subti.next.configuracao.domain.model.Contrato;
import br.com.subti.next.configuracao.domain.model.ContratoSistema;
import br.com.subti.next.configuracao.domain.model.EventoProcessado;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.Sla;
import br.com.subti.next.configuracao.domain.model.SlaSistema;
import br.com.subti.next.configuracao.domain.porta.PortfolioGateway;
import br.com.subti.next.configuracao.domain.porta.PortfolioGateway.SistemaPortfolio;
import br.com.subti.next.configuracao.domain.repositorio.ContratoRepository;
import br.com.subti.next.configuracao.domain.repositorio.EventoProcessadoRepository;
import br.com.subti.next.configuracao.domain.repositorio.SlaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vínculo com o Portfólio (N04, RF10, RN19, RN20) e consumo idempotente de eventos. */
@Service
@Transactional
public class PortfolioService {

    private final PortfolioGateway portfolio;
    private final ContratoRepository contratos;
    private final SlaRepository slas;
    private final SlaService slaService;
    private final ContratoService contratoService;
    private final EventoProcessadoRepository processados;
    private final HistoricoService historico;
    private final OutboxService outbox;

    public PortfolioService(PortfolioGateway portfolio, ContratoRepository contratos, SlaRepository slas,
                            SlaService slaService, ContratoService contratoService,
                            EventoProcessadoRepository processados, HistoricoService historico, OutboxService outbox) {
        this.portfolio = portfolio;
        this.contratos = contratos;
        this.slas = slas;
        this.slaService = slaService;
        this.contratoService = contratoService;
        this.processados = processados;
        this.historico = historico;
        this.outbox = outbox;
    }

    /** RF10: sistemas do Portfólio com vínculos; RN20 sinaliza sistema ativo sem SLA vigente. */
    @Transactional(readOnly = true)
    public Sistemas listar(String termo, boolean somentePendentes) {
        exigirPerfil(Suporte.ADMINISTRADOR, Suporte.GESTOR_TI, Suporte.N1);
        List<SistemaPortfolio> lista;
        try {
            lista = portfolio.listar(termo);
        } catch (RuntimeException e) {
            return new Sistemas(false, List.of());
        }
        LocalDate hoje = hoje();
        List<SistemaSaida> saida = lista.stream().map(s -> saida(s, hoje))
                .filter(s -> !somentePendentes || s.pendenciaConfiguracao()).toList();
        return new Sistemas(portfolio.disponivel(), saida);
    }

    SistemaSaida saida(SistemaPortfolio s, LocalDate hoje) {
        List<Contrato> doSistema = contratos.findBySistemasSistemaId(s.id());
        List<Sla> slasDoSistema = slas.findBySistemasSistemaId(s.id());
        boolean vigente = slasDoSistema.stream().filter(Sla::ativo)
                .anyMatch(x -> slaService.versaoVigenteEm(x.getId(), hoje).isPresent());
        return new SistemaSaida(s.id(), s.sigla(), s.nome(), s.situacao(), s.areaResponsavel(),
                doSistema.stream().map(c -> new Vinculo(c.getId(), c.getNumero(), c.getObjeto())).toList(),
                slasDoSistema.stream().map(x -> new Vinculo(x.getId(), x.getCodigo(), x.getNome())).toList(),
                vigente, s.ativo() && !vigente);
    }

    @Transactional(readOnly = true)
    public long contarPendencias() {
        try {
            LocalDate hoje = hoje();
            return portfolio.listar(null).stream().map(s -> saida(s, hoje)).filter(SistemaSaida::pendenciaConfiguracao).count();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public void vincularContrato(UUID contratoId, UUID sistemaId) {
        exigirGestao();
        Contrato c = contratoService.contrato(contratoId);
        SistemaPortfolio s = sistema(sistemaId);
        if (c.getSistemas().stream().noneMatch(x -> x.getSistemaId().equals(sistemaId))) {
            c.getSistemas().add(new ContratoSistema(sistemaId, s.nome()));
            contratos.saveAndFlush(c);
            historico.acao(c.tipoEntidade(), c.getId(), "SISTEMA_VINCULADO", s.nome() + " (" + s.sigla() + ")", null);
        }
    }

    public void desvincularContrato(UUID contratoId, UUID sistemaId) {
        exigirGestao();
        Contrato c = contratoService.contrato(contratoId);
        c.getSistemas().stream().filter(x -> x.getSistemaId().equals(sistemaId)).findFirst().ifPresent(x -> {
            c.getSistemas().remove(x);
            contratos.saveAndFlush(c);
            historico.acao(c.tipoEntidade(), c.getId(), "SISTEMA_DESVINCULADO", x.getSistemaNome(), null);
        });
    }

    public void vincularSla(UUID slaId, UUID sistemaId) {
        exigirGestao();
        Sla sla = slaService.sla(slaId);
        SistemaPortfolio s = sistema(sistemaId);
        if (sla.getSistemas().stream().noneMatch(x -> x.getSistemaId().equals(sistemaId))) {
            sla.getSistemas().add(new SlaSistema(sistemaId, s.nome()));
            slas.saveAndFlush(sla);
            historico.acao(sla.tipoEntidade(), sla.getId(), "SISTEMA_VINCULADO", s.nome() + " (" + s.sigla() + ")", null);
        }
    }

    public void desvincularSla(UUID slaId, UUID sistemaId) {
        exigirGestao();
        Sla sla = slaService.sla(slaId);
        sla.getSistemas().stream().filter(x -> x.getSistemaId().equals(sistemaId)).findFirst().ifPresent(x -> {
            sla.getSistemas().remove(x);
            slas.saveAndFlush(sla);
            historico.acao(sla.tipoEntidade(), sla.getId(), "SISTEMA_DESVINCULADO", x.getSistemaNome(), null);
        });
    }

    private SistemaPortfolio sistema(UUID id) {
        return portfolio.buscar(id).orElseThrow(() -> erroCampo("sistemaId", "Sistema não encontrado no Portfólio."));
    }

    /**
     * Consumidor idempotente de eventos do Portfólio (deduplicação pelo id). RN19: atualiza a cópia do
     * nome do sistema; RN20: sistema ativado sem SLA vigente gera evento de pendência de configuração.
     */
    public EventoResultado consumir(EventoEntrada e) {
        if (processados.existsById(e.id())) {
            return new EventoResultado(e.id(), false, "Evento já processado (ignorado).");
        }
        String mensagem = "Evento sem efeito neste módulo.";
        if (e.type().startsWith("portfolio.sistema.") && e.payload() != null && e.payload().get("sistemaId") != null) {
            UUID sistemaId = UUID.fromString(String.valueOf(e.payload().get("sistemaId")));
            Object nome = e.payload().get("nome");
            int atualizados = 0;
            if (nome != null) {
                for (Contrato c : contratos.findBySistemasSistemaId(sistemaId)) {
                    for (ContratoSistema cs : c.getSistemas()) {
                        if (cs.getSistemaId().equals(sistemaId) && !cs.getSistemaNome().equals(nome.toString())) {
                            cs.setSistemaNome(nome.toString());
                            atualizados++;
                        }
                    }
                }
                for (Sla s : slas.findBySistemasSistemaId(sistemaId)) {
                    for (SlaSistema ss : s.getSistemas()) {
                        if (ss.getSistemaId().equals(sistemaId) && !ss.getSistemaNome().equals(nome.toString())) {
                            ss.setSistemaNome(nome.toString());
                            atualizados++;
                        }
                    }
                }
            }
            mensagem = "Nomes de exibição atualizados: " + atualizados + ".";
            if (e.type().equals("portfolio.sistema.ativado")) {
                LocalDate hoje = hoje();
                boolean vigente = slas.findBySistemasSistemaId(sistemaId).stream().filter(x -> x.getSituacao() == Situacao.ATIVO)
                        .anyMatch(x -> slaService.versaoVigenteEm(x.getId(), hoje).isPresent());
                if (!vigente) {
                    outbox.registrar("configuracao.sistema.pendente-configuracao", Map.of("sistemaId", sistemaId.toString(),
                            "nome", nome == null ? "" : nome.toString()));
                    mensagem += " Sistema sem SLA vigente: pendência de configuração sinalizada.";
                }
            }
        }
        processados.save(new EventoProcessado(e.id(), e.type()));
        return new EventoResultado(e.id(), true, mensagem);
    }
}
