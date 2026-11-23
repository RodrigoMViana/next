package br.com.subti.next.configuracao.infrastructure.integracao;

import br.com.subti.next.configuracao.application.CadastroApoioService;
import br.com.subti.next.configuracao.application.CalendarioService;
import br.com.subti.next.configuracao.application.ContratadaService;
import br.com.subti.next.configuracao.application.ContratoService;
import br.com.subti.next.configuracao.application.NotificacaoService;
import br.com.subti.next.configuracao.application.SlaService;
import br.com.subti.next.configuracao.application.Suporte;
import br.com.subti.next.configuracao.application.dto.ApoioDtos;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos;
import br.com.subti.next.configuracao.application.dto.ContratoDtos;
import br.com.subti.next.configuracao.application.dto.NotificacaoDtos;
import br.com.subti.next.configuracao.application.dto.SlaDtos;
import br.com.subti.next.configuracao.domain.model.CalendarioFeriado.Abrangencia;
import br.com.subti.next.configuracao.domain.model.Contratada;
import br.com.subti.next.configuracao.domain.model.ContratadaMembro;
import br.com.subti.next.configuracao.domain.model.ContratoFiscal;
import br.com.subti.next.configuracao.domain.model.Servico;
import br.com.subti.next.configuracao.domain.model.Sla;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.regra.CalculoSla.FormaContagem;
import br.com.subti.next.configuracao.domain.regra.CalculoSla.UnidadeMeta;
import br.com.subti.next.configuracao.domain.regra.Cnpj;
import br.com.subti.next.configuracao.domain.repositorio.ContratadaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carga de dados fictícios para desenvolvimento, homologação, treinamento e demonstração (Premissa 8).
 * Usa os próprios casos de uso, de modo que todas as regras de negócio são aplicadas. Executa apenas
 * quando o banco não tem contratadas e next.demo.carregar=true.
 */
@Component
@ConditionalOnProperty(name = "next.demo.carregar", havingValue = "true")
public class CargaDemonstracao implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CargaDemonstracao.class);

    private final ContratadaRepository contratadas;
    private final CadastroApoioService apoio;
    private final CalendarioService calendarios;
    private final ContratadaService contratadaService;
    private final ContratoService contratos;
    private final SlaService slas;
    private final NotificacaoService notificacoes;
    private final TransactionTemplate tx;

    public CargaDemonstracao(ContratadaRepository contratadas, CadastroApoioService apoio, CalendarioService calendarios,
                             ContratadaService contratadaService, ContratoService contratos, SlaService slas,
                             NotificacaoService notificacoes, TransactionTemplate tx) {
        this.contratadas = contratadas;
        this.apoio = apoio;
        this.calendarios = calendarios;
        this.contratadaService = contratadaService;
        this.contratos = contratos;
        this.slas = slas;
        this.notificacoes = notificacoes;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (contratadas.count() > 0) {
            return;
        }
        UsuarioAtual.set(new UsuarioAtual(DadosFicticios.U_ADMIN, "Carga de demonstração",
                Set.of(Suporte.ADMINISTRADOR, Suporte.GESTOR_TI), null));
        try {
            carregar();
            LOG.info("Dados fictícios de demonstração carregados.");
        } finally {
            UsuarioAtual.limpar();
        }
    }

    private void carregar() {
        LocalDate hoje = Suporte.hoje();
        var pf = apoio.criarUnidade(new ApoioDtos.UnidadeMedidaEntrada("PF", "Ponto de Função",
                "Medida funcional (IFPUG/SISP). Apurada pela Engenharia: PF estimado e realizado."));
        var hora = apoio.criarUnidade(new ApoioDtos.UnidadeMedidaEntrada("H", "Hora técnica", "Hora de trabalho técnico."));
        var ust = apoio.criarUnidade(new ApoioDtos.UnidadeMedidaEntrada("UST", "Unidade de Serviço Técnico",
                "Unidade para serviços técnicos de sustentação."));

        var tesouro = apoio.criarFonte(new ApoioDtos.FonteRecursoEntrada("FR-1500", "Recursos ordinários do tesouro",
                "Fonte fictícia para demonstração."));
        var convenio = apoio.criarFonte(new ApoioDtos.FonteRecursoEntrada("FR-1700", "Convênios e transferências",
                "Fonte fictícia para demonstração."));
        apoio.criarFonte(new ApoioDtos.FonteRecursoEntrada("FR-1759", "Operação de crédito (programa de modernização)", null));

        var dev = apoio.criarServico(new ApoioDtos.ServicoEntrada("DEV-PF", "Desenvolvimento de software",
                "Novas funcionalidades medidas em PF.", Servico.Tipo.DESENVOLVIMENTO, pf.id()));
        var man = apoio.criarServico(new ApoioDtos.ServicoEntrada("MAN-PF", "Manutenção evolutiva e corretiva",
                null, Servico.Tipo.MANUTENCAO, pf.id()));
        var sus = apoio.criarServico(new ApoioDtos.ServicoEntrada("SUS-H", "Sustentação de sistemas",
                "Atendimento N2/N2+ de chamados.", Servico.Tipo.SUSTENTACAO, hora.id()));
        var sup = apoio.criarServico(new ApoioDtos.ServicoEntrada("SUP-UST", "Suporte técnico especializado",
                null, Servico.Tipo.SUSTENTACAO, ust.id()));

        List<CalendarioDtos.Expediente> exp = new ArrayList<>();
        for (int d = 1; d <= 5; d++) {
            exp.add(new CalendarioDtos.Expediente(d, LocalTime.of(8, 0), LocalTime.of(12, 0)));
            exp.add(new CalendarioDtos.Expediente(d, LocalTime.of(14, 0), LocalTime.of(18, 0)));
        }
        List<CalendarioDtos.Feriado> feriados = List.of(
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 1, 1), "Confraternização Universal", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 4, 21), "Tiradentes", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 5, 1), "Dia do Trabalho", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 9, 7), "Independência do Brasil", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 10, 12), "Nossa Senhora Aparecida", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 11, 2), "Finados", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 11, 15), "Proclamação da República", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 11, 20), "Dia Nacional de Zumbi e da Consciência Negra", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2026, 12, 25), "Natal", Abrangencia.NACIONAL, true),
                new CalendarioDtos.Feriado(null, LocalDate.of(2027, 2, 8), "Carnaval", Abrangencia.PONTO_FACULTATIVO, false),
                new CalendarioDtos.Feriado(null, LocalDate.of(2027, 2, 9), "Carnaval", Abrangencia.PONTO_FACULTATIVO, false),
                new CalendarioDtos.Feriado(null, LocalDate.of(2027, 3, 26), "Sexta-feira Santa", Abrangencia.NACIONAL, false));
        var comercial = calendarios.criar(new CalendarioDtos.CalendarioEntrada("Expediente administrativo",
                "Segunda a sexta, 8h às 12h e 14h às 18h, feriados nacionais.", "America/Sao_Paulo",
                LocalDate.of(2026, 1, 1), exp, feriados));
        List<CalendarioDtos.Expediente> plantao = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            plantao.add(new CalendarioDtos.Expediente(d, LocalTime.of(7, 0), LocalTime.of(22, 0)));
        }
        calendarios.criar(new CalendarioDtos.CalendarioEntrada("Plantão estendido", "Todos os dias, 7h às 22h.",
                "America/Sao_Paulo", LocalDate.of(2026, 1, 1), plantao, List.of()));

        // Contratadas com identificadores fixos (os usuários N2 do login de desenvolvimento apontam para elas)
        tx.executeWithoutResult(s -> {
            contratadas.save(contratada(DadosFicticios.FABRICA_ALFA, "112223330001", "Fábrica Alfa Tecnologia Ltda.", "Fábrica Alfa"));
            contratadas.save(contratada(DadosFicticios.FABRICA_BETA, "12ABC34501DE", "Fábrica Beta Sistemas S.A.", "Fábrica Beta"));
        });
        contratadaService.adicionarMembro(DadosFicticios.FABRICA_ALFA, new ContratadaDtos.MembroEntrada(DadosFicticios.U_N2_ALFA,
                ContratadaMembro.Papel.N2_GESTOR, LocalDate.of(2026, 1, 1), null));
        contratadaService.adicionarMembro(DadosFicticios.FABRICA_ALFA, new ContratadaDtos.MembroEntrada(DadosFicticios.U_TEC_ALFA,
                ContratadaMembro.Papel.N2_TECNICO, LocalDate.of(2026, 1, 1), null));
        contratadaService.adicionarMembro(DadosFicticios.FABRICA_BETA, new ContratadaDtos.MembroEntrada(DadosFicticios.U_N2_BETA,
                ContratadaMembro.Papel.N2_GESTOR, LocalDate.of(2026, 1, 1), null));
        contratadaService.registrarAlteracao(DadosFicticios.FABRICA_BETA, new ContratadaDtos.AlteracaoEntrada(
                LocalDate.of(2026, 7, 15), "ALTERACAO_SOCIETARIA", "Ingresso de novo sócio administrador (dado fictício)."), null);

        var ctAlfa = contratos.criar(new ContratoDtos.ContratoEntrada("CT-2026-0007",
                "Desenvolvimento, manutenção e sustentação dos sistemas da Receita.", DadosFicticios.FABRICA_ALFA,
                comercial.id(), LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 14),
                List.of(new ContratoDtos.ItemEntrada(dev.id(), new BigDecimal("3000"), new BigDecimal("520.00")),
                        new ContratoDtos.ItemEntrada(man.id(), new BigDecimal("1500"), new BigDecimal("480.00")),
                        new ContratoDtos.ItemEntrada(sus.id(), new BigDecimal("8760"), new BigDecimal("95.50"))),
                List.of(tesouro.id()), List.of(DadosFicticios.SIS_ARRECADACAO, DadosFicticios.SIS_PROTOCOLO),
                List.of(new ContratoDtos.FiscalEntrada(DadosFicticios.U_FISCAL_TEC, ContratoFiscal.TipoFiscal.FISCAL_TECNICO,
                                LocalDate.of(2026, 1, 15), null),
                        new ContratoDtos.FiscalEntrada(DadosFicticios.U_FISCAL_ADM, ContratoFiscal.TipoFiscal.FISCAL_ADMINISTRATIVO,
                                LocalDate.of(2026, 1, 15), null))));
        contratos.registrarAditivo(ctAlfa.id(), new ContratoDtos.AditivoEntrada("1º TA", false, true, null,
                new BigDecimal("120000.00"), LocalDate.of(2026, 6, 10), "Acréscimo de 9,6% no volume de desenvolvimento."));

        var ctBeta = contratos.criar(new ContratoDtos.ContratoEntrada("CT-2025-0031",
                "Sustentação do Portal do Cidadão e da Folha de Pagamento.", DadosFicticios.FABRICA_BETA,
                comercial.id(), LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28),
                List.of(new ContratoDtos.ItemEntrada(sup.id(), new BigDecimal("12000"), new BigDecimal("70.00"))),
                List.of(convenio.id()), List.of(DadosFicticios.SIS_PORTAL),
                List.of(new ContratoDtos.FiscalEntrada(DadosFicticios.U_FISCAL_REQ, ContratoFiscal.TipoFiscal.GESTOR_CONTRATO,
                        LocalDate.of(2025, 3, 1), null))));
        contratos.registrarAditivo(ctBeta.id(), new ContratoDtos.AditivoEntrada("1º TA", true, false,
                LocalDate.of(2027, 2, 28), null, LocalDate.of(2026, 2, 20), "Prorrogação por 12 meses (art. 107 da Lei 14.133/2021)."));

        // SLA da Fábrica Alfa: versão 1 vigente (parecer favorável) e SLA da equipe interna em rascunho
        var slaAlfa = slas.criar(new SlaDtos.SlaEntrada("SLA-ALFA", "SLA de sustentação - Fábrica Alfa",
                Sla.TipoResponsavel.CONTRATADA, ctAlfa.id(), null, List.of(sus.id()), List.of(DadosFicticios.SIS_ARRECADACAO),
                List.of(new SlaDtos.RegraEntrada("Prioridade crítica", "Tempo de primeira resposta", new BigDecimal("30"),
                                UnidadeMeta.MINUTOS, FormaContagem.CORRIDO_24X7, null, false, null),
                        new SlaDtos.RegraEntrada("Prioridade crítica", "Tempo de solução", new BigDecimal("4"),
                                UnidadeMeta.HORAS, FormaContagem.HORARIO_COMERCIAL, comercial.id(), true, null),
                        new SlaDtos.RegraEntrada("Prioridade normal", "Tempo de solução", new BigDecimal("3"),
                                UnidadeMeta.DIAS_UTEIS, FormaContagem.HORARIO_COMERCIAL, comercial.id(), true, null),
                        new SlaDtos.RegraEntrada("Serviço", "Disponibilidade mensal", new BigDecimal("99.5"),
                                UnidadeMeta.PERCENTUAL, FormaContagem.CORRIDO_24X7, null, false, "Medida mensal"))));
        UUID v1 = slaAlfa.versoes().getFirst().id();
        slas.enviarParaAvaliacao(slaAlfa.id(), v1);
        UsuarioAtual.set(new UsuarioAtual(DadosFicticios.U_N2_ALFA, "Fábio Gestor (Fábrica Alfa)", Set.of(Suporte.N2_GESTOR),
                DadosFicticios.FABRICA_ALFA));
        slas.registrarParecer(slaAlfa.id(), v1, new SlaDtos.Parecer(true, "De acordo com as metas propostas."));
        UsuarioAtual.set(new UsuarioAtual(DadosFicticios.U_GESTOR_TI, "Gustavo Gestor de TI", Set.of(Suporte.GESTOR_TI), null));
        slas.decidir(slaAlfa.id(), v1, new SlaDtos.Decisao(true, hoje, "Aprovado na reunião de alinhamento."));

        slas.criar(new SlaDtos.SlaEntrada("SLA-N1", "SLA da equipe interna (N1)", Sla.TipoResponsavel.EQUIPE_INTERNA, null,
                "Central de atendimento N1", List.of(sus.id()), List.of(DadosFicticios.SIS_FOLHA),
                List.of(new SlaDtos.RegraEntrada("Todas", "Tempo de triagem", new BigDecimal("2"), UnidadeMeta.HORAS,
                        FormaContagem.HORARIO_COMERCIAL, comercial.id(), true, null))));

        notificacoes.criar(new NotificacaoDtos.NotificacaoEntrada("Alerta de SLA em risco", "sustentacao.sla.em-risco",
                true, true, Set.of("N1", "N2_GESTOR"), "Chamado {{chamado.codigo}} com SLA em risco",
                "O chamado {{chamado.codigo}} consumiu {{sla.percentual}} do prazo do {{sla.codigo}}. Prazo: {{sla.prazo}}.", 80));
        notificacoes.criar(new NotificacaoDtos.NotificacaoEntrada("SLA violado", "sustentacao.sla.violado", true, true,
                Set.of("GESTOR_TI", "N2_GESTOR"), "SLA violado: {{chamado.codigo}}",
                "O prazo do {{sla.codigo}} para o chamado {{chamado.codigo}} venceu em {{sla.prazo}}.", null));
        notificacoes.criar(new NotificacaoDtos.NotificacaoEntrada("Versão de SLA em avaliação", "configuracao.sla.em-avaliacao",
                true, false, Set.of("N2_GESTOR"), "SLA {{sla.codigo}} aguardando seu parecer",
                "A versão {{sla.versao}} do {{sla.nome}} foi enviada para avaliação.", null));
    }

    private static Contratada contratada(UUID id, String base12, String razao, String fantasia) {
        Contratada c = new Contratada();
        c.definirId(id);
        c.setCnpj(base12 + Cnpj.calcularDigitos(base12));
        c.setRazaoSocial(razao);
        c.setNomeFantasia(fantasia);
        return c;
    }
}
