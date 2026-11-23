package br.com.subti.next.configuracao.api;

import static br.com.subti.next.configuracao.api.Respostas.IF_MATCH;

import br.com.subti.next.configuracao.application.CalendarioService;
import br.com.subti.next.configuracao.application.HistoricoService;
import br.com.subti.next.configuracao.application.Pagina;
import br.com.subti.next.configuracao.application.Suporte;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.MudancaSituacao;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.CalendarioEntrada;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.CalendarioSaida;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.SimulacaoEntrada;
import br.com.subti.next.configuracao.application.dto.CalendarioDtos.SimulacaoSaida;
import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.domain.model.Situacao;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/** API interna: calendários (N10). */
@RestController
@RequestMapping("/internal/v1/calendarios")
@Tag(name = "Calendários", description = "N10 - expediente, feriados e simulação de prazo")
public class CalendarioController {

    private final CalendarioService servico;
    private final HistoricoService historico;

    public CalendarioController(CalendarioService servico, HistoricoService historico) {
        this.servico = servico;
        this.historico = historico;
    }

    @GetMapping
    public Pagina<CalendarioSaida> listar(@RequestParam(required = false) String termo,
                                          @RequestParam(required = false) Situacao situacao,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String sort) {
        return servico.listar(termo, situacao, page, size, sort);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CalendarioSaida> obter(@PathVariable UUID id) {
        CalendarioSaida s = servico.obter(id);
        return Respostas.ok(s, s.version());
    }

    @PostMapping
    public ResponseEntity<CalendarioSaida> criar(@Valid @RequestBody CalendarioEntrada e) {
        CalendarioSaida s = servico.criar(e);
        return Respostas.criado("/internal/v1/calendarios", s.id(), s, s.version());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CalendarioSaida> alterar(@PathVariable UUID id,
                                                   @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                   @Valid @RequestBody CalendarioEntrada e) {
        CalendarioSaida s = servico.alterar(id, ifMatch, e);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/{id}/desativacao")
    public CalendarioSaida desativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, false, e.justificativa());
    }

    @PostMapping("/{id}/reativacao")
    public CalendarioSaida reativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, true, e.justificativa());
    }

    @PostMapping("/{id}/simulacoes")
    public SimulacaoSaida simular(@PathVariable UUID id, @Valid @RequestBody SimulacaoEntrada e) {
        return servico.simular(id, e);
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoDto> historico(@PathVariable UUID id) {
        Suporte.exigirLeitura();
        return historico.listar("calendario", id);
    }
}
