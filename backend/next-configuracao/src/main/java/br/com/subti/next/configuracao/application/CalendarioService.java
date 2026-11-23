package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.erroCampo;
import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.exigirLeitura;
import static br.com.subti.next.configuracao.application.Suporte.hoje;
import static br.com.subti.next.configuracao.application.Suporte.regra;
import static br.com.subti.next.configuracao.application.Suporte.termo;
import static br.com.subti.next.configuracao.application.Suporte.texto;
import static br.com.subti.next.configuracao.application.Suporte.verificarVersao;

import br.com.subti.next.configuracao.application.dto.CalendarioDtos.CalendarioEntrada;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.CalendarioSaida;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.Expediente;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.Feriado;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.SimulacaoEntrada;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.SimulacaoSaida;
import br.com.subti.next.configuracao.domain.excecao.ConflitoException;
import br.com.subti.next.configuracao.domain.excecao.RecursoNaoEncontradoException;
import br.com.subti.next.configuracao.domain.model.Calendario;
import br.com.subti.next.configuracao.domain.model.CalendarioExpediente;
import br.com.subti.next.configuracao.domain.model.CalendarioFeriado;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.regra.CalculadoraExpediente;
import br.com.subti.next.configuracao.domain.regra.CalculoSla;
import br.com.subti.next.configuracao.domain.repositorio.CalendarioRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratoRepository;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do calendário (N10, RF15, RN17, RN18). */
@Service
@Transactional
public class CalendarioService {

    private static final Map<String, String> ORDEM = Map.of("nome", "nome", "situacao", "situacao",
            "vigenciaInicio", "vigenciaInicio");

    private final CalendarioRepository calendarios;
    private final ContratoRepository contratos;
    private final HistoricoService historico;
    private final OutboxService outbox;

    public CalendarioService(CalendarioRepository calendarios, ContratoRepository contratos, HistoricoService historico,
                             OutboxService outbox) {
        this.calendarios = calendarios;
        this.contratos = contratos;
        this.historico = historico;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Pagina<CalendarioSaida> listar(String busca, Situacao situacao, Integer page, Integer size, String sort) {
        exigirLeitura();
        return Pagina.de(calendarios.pesquisar(termo(busca), situacao, Pagina.pageable(page, size, sort, ORDEM, "nome")),
                CalendarioSaida::de);
    }

    @Transactional(readOnly = true)
    public CalendarioSaida obter(UUID id) {
        exigirLeitura();
        return CalendarioSaida.de(calendario(id));
    }

    public CalendarioSaida criar(CalendarioEntrada e) {
        exigirGestao();
        Calendario c = new Calendario();
        preencher(c, e, true);
        calendarios.save(c);
        historico.criacao(c);
        return CalendarioSaida.de(c);
    }

    /** RN18: a alteração vale a partir da vigência informada (hoje ou futura) e não recalcula SLAs em andamento. */
    public CalendarioSaida alterar(UUID id, String ifMatch, CalendarioEntrada e) {
        exigirGestao();
        Calendario c = calendario(id);
        verificarVersao(c, ifMatch);
        var antes = c.snapshot();
        preencher(c, e, false);
        calendarios.saveAndFlush(c);
        historico.alteracao(c, antes, null);
        outbox.registrar("configuracao.calendario.alterado", Map.of("calendarioId", c.getId().toString(),
                "vigenciaInicio", c.getVigenciaInicio().toString()));
        return CalendarioSaida.de(c);
    }

    public CalendarioSaida mudarSituacao(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        Calendario c = calendario(id);
        long emUso = contratos.countByCalendarioIdAndSituacao(id, Situacao.ATIVO);
        if (!ativar && emUso > 0 && texto(justificativa) == null) {
            throw erroCampo("justificativa", "O calendário está em uso por " + emUso
                    + " contrato(s) ativo(s). Informe a justificativa da desativação.");
        }
        regra("situacao", () -> {
            if (ativar) {
                c.reativar(texto(justificativa));
            } else {
                c.desativar(texto(justificativa));
            }
        });
        calendarios.saveAndFlush(c);
        historico.acao(c.tipoEntidade(), c.getId(), ativar ? HistoricoService.REATIVACAO : HistoricoService.DESATIVACAO,
                "Situação: " + c.getSituacao(), texto(justificativa));
        return CalendarioSaida.de(c);
    }

    /** Simula o prazo de uma meta neste calendário (apoio à parametrização do SLA). */
    @Transactional(readOnly = true)
    public SimulacaoSaida simular(UUID id, SimulacaoEntrada e) {
        exigirLeitura();
        Calendario c = calendario(id);
        CalculadoraExpediente calc = c.calculadora();
        Instant prazo = regra("meta", () -> {
            CalculoSla.validarRegra(e.formaContagem(), e.unidade(), e.meta(), true);
            return CalculoSla.prazo(e.inicio(), e.formaContagem(), e.unidade(), e.meta(), calc);
        });
        Duration util = e.formaContagem() == CalculoSla.FormaContagem.CORRIDO_24X7
                ? Duration.between(e.inicio(), prazo) : calc.tempoUtil(e.inicio(), prazo);
        return new SimulacaoSaida(e.inicio(), prazo, util.toMinutes(), c.getFusoHorario());
    }

    private void preencher(Calendario c, CalendarioEntrada e, boolean novo) {
        if (calendarios.existsByNomeIgnoreCaseAndIdNot(e.nome().trim(), c.getId())) {
            throw new ConflitoException("nome-duplicado", "Nome já cadastrado", "nome",
                    "Já existe um calendário com este nome.");
        }
        try {
            ZoneId.of(e.fusoHorario());
        } catch (DateTimeException ex) {
            throw erroCampo("fusoHorario", "Fuso horário inválido. Use o formato IANA, por exemplo America/Sao_Paulo.");
        }
        if (!novo && e.vigenciaInicio().isBefore(hoje())) {
            throw erroCampo("vigenciaInicio", "A alteração vale a partir da vigência informada: use hoje ou uma data futura.");
        }
        validarExpediente(e.expedientes());
        validarFeriados(e.feriados());

        c.setNome(e.nome().trim());
        c.setDescricao(texto(e.descricao()));
        c.setFusoHorario(e.fusoHorario());
        c.setVigenciaInicio(e.vigenciaInicio());
        c.getExpedientes().clear();
        e.expedientes().stream()
                .sorted(Comparator.comparingInt(Expediente::diaSemana).thenComparing(Expediente::horaInicio))
                .forEach(x -> c.getExpedientes().add(new CalendarioExpediente(x.diaSemana(), x.horaInicio(), x.horaFim())));
        c.getFeriados().clear();
        e.feriados().stream().sorted(Comparator.comparing(Feriado::data))
                .forEach(f -> c.getFeriados().add(new CalendarioFeriado(f.data(), f.descricao().trim(), f.abrangencia(),
                        f.recorrenteAnual())));
    }

    private void validarExpediente(List<Expediente> expedientes) {
        if (expedientes.isEmpty()) {
            throw erroCampo("expedientes", "Informe ao menos um intervalo de expediente.");
        }
        for (int d = 1; d <= 7; d++) {
            int dia = d;
            List<CalculadoraExpediente.Intervalo> lista = new ArrayList<>();
            for (Expediente x : expedientes.stream().filter(x -> x.diaSemana() == dia).toList()) {
                lista.add(regra("expedientes", () -> new CalculadoraExpediente.Intervalo(x.horaInicio(), x.horaFim())));
            }
            lista.sort(Comparator.comparing(CalculadoraExpediente.Intervalo::inicio));
            regra("expedientes", () -> CalculadoraExpediente.validarSobreposicao(DayOfWeek.of(dia), lista));
        }
    }

    private void validarFeriados(List<Feriado> feriados) {
        Set<String> chaves = new HashSet<>();
        for (Feriado f : feriados) {
            String chave = f.recorrenteAnual() ? f.data().getMonthValue() + "-" + f.data().getDayOfMonth()
                    : f.data().toString();
            if (!chaves.add(chave)) {
                throw erroCampo("feriados", "Feriado repetido em " + f.data() + ".");
            }
        }
    }

    Calendario calendario(UUID id) {
        return calendarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Calendário", id));
    }
}
