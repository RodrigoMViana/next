package br.com.subti.next.configuracao.api;

import static br.com.subti.next.configuracao.api.Respostas.IDEMPOTENCY_KEY;
import static br.com.subti.next.configuracao.api.Respostas.IF_MATCH;

import br.com.subti.next.configuracao.application.HistoricoService;
import br.com.subti.next.configuracao.application.IdempotenciaService;
import br.com.subti.next.configuracao.application.Pagina;
import br.com.subti.next.configuracao.application.PortfolioService;
import br.com.subti.next.configuracao.application.SlaService;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.MudancaSituacao;
import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.VinculoEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.Decisao;
import br.com.subti.next.configuracao.application.dto.SlaDtos.NovaVersao;
import br.com.subti.next.configuracao.application.dto.SlaDtos.Parecer;
import br.com.subti.next.configuracao.application.dto.SlaDtos.RegrasEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SimulacaoEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SimulacaoSaida;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SlaDetalhe;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SlaEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SlaResumo;
import br.com.subti.next.configuracao.application.dto.SlaDtos.VersaoSaida;
import br.com.subti.next.configuracao.application.dto.SlaDtos.VersaoVigente;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.Sla;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API interna: SLA parametrizável, versões, parecer e decisão de vigência (N03). */
@RestController
@RequestMapping("/internal/v1/slas")
@Tag(name = "SLA", description = "N03 - acordos de nível de serviço")
public class SlaController {

    private final SlaService servico;
    private final HistoricoService historico;
    private final IdempotenciaService idempotencia;
    private final PortfolioService portfolio;

    public SlaController(SlaService servico, HistoricoService historico, IdempotenciaService idempotencia,
                         PortfolioService portfolio) {
        this.servico = servico;
        this.historico = historico;
        this.idempotencia = idempotencia;
        this.portfolio = portfolio;
    }

    @GetMapping
    public Pagina<SlaResumo> listar(@RequestParam(required = false) String termo,
                                    @RequestParam(required = false) Situacao situacao,
                                    @RequestParam(required = false) Sla.TipoResponsavel tipo,
                                    @RequestParam(required = false) UUID contratoId,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String sort) {
        return servico.listar(termo, situacao, tipo, contratoId, page, size, sort);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SlaDetalhe> obter(@PathVariable UUID id) {
        SlaDetalhe d = servico.obter(id);
        return Respostas.ok(d, d.version());
    }

    @PostMapping
    public ResponseEntity<SlaDetalhe> criar(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String chave,
                                            @Valid @RequestBody SlaEntrada e) {
        var r = idempotencia.executar(chave, "POST /slas", () -> servico.criar(e), SlaDetalhe::id, servico::obter);
        return r.repetido() ? Respostas.repetido(r.corpo(), r.corpo().version())
                : Respostas.criado("/internal/v1/slas", r.corpo().id(), r.corpo(), r.corpo().version());
    }

    @PutMapping("/{id}")
    public ResponseEntity<SlaDetalhe> alterar(@PathVariable UUID id,
                                              @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody SlaEntrada e) {
        SlaDetalhe d = servico.alterar(id, ifMatch, e);
        return Respostas.ok(d, d.version());
    }

    @PostMapping("/{id}/desativacao")
    public SlaDetalhe desativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, false, e.justificativa());
    }

    @PostMapping("/{id}/reativacao")
    public SlaDetalhe reativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, true, e.justificativa());
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoDto> historico(@PathVariable UUID id) {
        servico.obter(id);
        return historico.listar("sla", id);
    }

    @GetMapping("/{id}/versao-vigente")
    public VersaoVigente vigente(@PathVariable UUID id, @RequestParam(required = false) LocalDate data) {
        return servico.vigenteEm(id, data);
    }

    @PostMapping("/{id}/versoes")
    public VersaoSaida novaVersao(@PathVariable UUID id, @Valid @RequestBody NovaVersao e) {
        return servico.novaVersao(id, e);
    }

    @PutMapping("/{id}/versoes/{versaoId}/regras")
    public ResponseEntity<VersaoSaida> regras(@PathVariable UUID id, @PathVariable UUID versaoId,
                                              @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody RegrasEntrada e) {
        VersaoSaida v = servico.salvarRegras(id, versaoId, ifMatch, e.regras());
        return Respostas.ok(v, v.version());
    }

    @PostMapping("/{id}/versoes/{versaoId}/envio-avaliacao")
    public VersaoSaida enviar(@PathVariable UUID id, @PathVariable UUID versaoId) {
        return servico.enviarParaAvaliacao(id, versaoId);
    }

    @PostMapping("/{id}/versoes/{versaoId}/parecer")
    public VersaoSaida parecer(@PathVariable UUID id, @PathVariable UUID versaoId, @Valid @RequestBody Parecer e) {
        return servico.registrarParecer(id, versaoId, e);
    }

    @PostMapping("/{id}/versoes/{versaoId}/decisao")
    public VersaoSaida decidir(@PathVariable UUID id, @PathVariable UUID versaoId,
                               @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String chave,
                               @Valid @RequestBody Decisao e) {
        return servico.decidir(id, versaoId, e);
    }

    @PostMapping("/{id}/simulacoes")
    public SimulacaoSaida simular(@PathVariable UUID id, @Valid @RequestBody SimulacaoEntrada e) {
        return servico.simular(id, e);
    }

    @PostMapping("/{id}/sistemas")
    public SlaDetalhe vincularSistema(@PathVariable UUID id, @Valid @RequestBody VinculoEntrada e) {
        portfolio.vincularSla(id, e.sistemaId());
        return servico.obter(id);
    }

    @DeleteMapping("/{id}/sistemas/{sistemaId}")
    public SlaDetalhe desvincularSistema(@PathVariable UUID id, @PathVariable UUID sistemaId) {
        portfolio.desvincularSla(id, sistemaId);
        return servico.obter(id);
    }
}
