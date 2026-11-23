package br.com.subti.next.configuracao.api;

import static br.com.subti.next.configuracao.api.Respostas.IDEMPOTENCY_KEY;
import static br.com.subti.next.configuracao.api.Respostas.IF_MATCH;

import br.com.subti.next.configuracao.application.ContratoService;
import br.com.subti.next.configuracao.application.HistoricoService;
import br.com.subti.next.configuracao.application.IdempotenciaService;
import br.com.subti.next.configuracao.application.Pagina;
import br.com.subti.next.configuracao.application.PortfolioService;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.MudancaSituacao;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.AditivoEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ContratoDetalhe;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ContratoEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ContratoResumo;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.EncerramentoFiscal;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.FiscalEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.SubstituicaoFiscal;
import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.application.dto.PortfolioDtos.VinculoEntrada;
import br.com.subti.next.configuracao.domain.model.Situacao;
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

/** API interna: contratos, aditivos, comissão de fiscalização e sistemas vinculados (N01, N04). */
@RestController
@RequestMapping("/internal/v1/contratos")
@Tag(name = "Contratos", description = "N01 - contratos, aditivos e fiscais")
public class ContratoController {

    private final ContratoService servico;
    private final HistoricoService historico;
    private final IdempotenciaService idempotencia;
    private final PortfolioService portfolio;

    public ContratoController(ContratoService servico, HistoricoService historico, IdempotenciaService idempotencia,
                              PortfolioService portfolio) {
        this.servico = servico;
        this.historico = historico;
        this.idempotencia = idempotencia;
        this.portfolio = portfolio;
    }

    @GetMapping
    public Pagina<ContratoResumo> listar(@RequestParam(required = false) String termo,
                                         @RequestParam(required = false) Situacao situacao,
                                         @RequestParam(required = false) UUID contratadaId,
                                         @RequestParam(required = false) LocalDate vigenteEm,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String sort) {
        return servico.listar(termo, situacao, contratadaId, vigenteEm, page, size, sort);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContratoDetalhe> obter(@PathVariable UUID id) {
        ContratoDetalhe d = servico.obter(id);
        return Respostas.ok(d, d.version());
    }

    @PostMapping
    public ResponseEntity<ContratoDetalhe> criar(@RequestHeader(value = IDEMPOTENCY_KEY, required = false) String chave,
                                                 @Valid @RequestBody ContratoEntrada e) {
        var r = idempotencia.executar(chave, "POST /contratos", () -> servico.criar(e), ContratoDetalhe::id, servico::obter);
        return r.repetido() ? Respostas.repetido(r.corpo(), r.corpo().version())
                : Respostas.criado("/internal/v1/contratos", r.corpo().id(), r.corpo(), r.corpo().version());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContratoDetalhe> alterar(@PathVariable UUID id,
                                                   @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                   @Valid @RequestBody ContratoEntrada e) {
        ContratoDetalhe d = servico.alterar(id, ifMatch, e);
        return Respostas.ok(d, d.version());
    }

    @PostMapping("/{id}/desativacao")
    public ContratoDetalhe desativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, false, e.justificativa());
    }

    @PostMapping("/{id}/reativacao")
    public ContratoDetalhe reativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, true, e.justificativa());
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoDto> historico(@PathVariable UUID id) {
        servico.obter(id);
        return historico.listar("contrato", id);
    }

    @PostMapping("/{id}/aditivos")
    public ResponseEntity<ContratoDetalhe> aditivo(@PathVariable UUID id,
                                                   @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String chave,
                                                   @Valid @RequestBody AditivoEntrada e) {
        var r = idempotencia.executar(chave, "POST /contratos/" + id + "/aditivos", () -> servico.registrarAditivo(id, e),
                ContratoDetalhe::id, servico::obter);
        return r.repetido() ? Respostas.repetido(r.corpo(), r.corpo().version()) : Respostas.ok(r.corpo(), r.corpo().version());
    }

    @PostMapping("/{id}/fiscais")
    public ContratoDetalhe designarFiscal(@PathVariable UUID id, @Valid @RequestBody FiscalEntrada e) {
        return servico.designarFiscal(id, e);
    }

    @PostMapping("/{id}/fiscais/{fiscalId}/substituicao")
    public ContratoDetalhe substituirFiscal(@PathVariable UUID id, @PathVariable UUID fiscalId,
                                            @Valid @RequestBody SubstituicaoFiscal e) {
        return servico.substituirFiscal(id, fiscalId, e);
    }

    @PostMapping("/{id}/fiscais/{fiscalId}/encerramento")
    public ContratoDetalhe encerrarFiscal(@PathVariable UUID id, @PathVariable UUID fiscalId,
                                          @Valid @RequestBody EncerramentoFiscal e) {
        return servico.encerrarFiscal(id, fiscalId, e);
    }

    @PostMapping("/{id}/sistemas")
    public ContratoDetalhe vincularSistema(@PathVariable UUID id, @Valid @RequestBody VinculoEntrada e) {
        portfolio.vincularContrato(id, e.sistemaId());
        return servico.obter(id);
    }

    @DeleteMapping("/{id}/sistemas/{sistemaId}")
    public ContratoDetalhe desvincularSistema(@PathVariable UUID id, @PathVariable UUID sistemaId) {
        portfolio.desvincularContrato(id, sistemaId);
        return servico.obter(id);
    }
}
