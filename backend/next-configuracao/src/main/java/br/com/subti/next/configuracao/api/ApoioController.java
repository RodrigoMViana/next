package br.com.subti.next.configuracao.api;

import static br.com.subti.next.configuracao.api.Respostas.IF_MATCH;

import br.com.subti.next.configuracao.application.CadastroApoioService;
import br.com.subti.next.configuracao.application.HistoricoService;
import br.com.subti.next.configuracao.application.Pagina;
import br.com.subti.next.configuracao.application.Suporte;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.FonteRecursoEntrada;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.FonteRecursoSaida;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.MudancaSituacao;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.ServicoEntrada;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.ServicoSaida;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.UnidadeMedidaEntrada;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.UnidadeMedidaSaida;
import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.domain.model.Servico;
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

/** API interna: unidades de medida (N07), fontes de recursos (N08) e serviços (N09). */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Cadastros de apoio", description = "N07, N08 e N09")
public class ApoioController {

    private final CadastroApoioService servico;
    private final HistoricoService historico;

    public ApoioController(CadastroApoioService servico, HistoricoService historico) {
        this.servico = servico;
        this.historico = historico;
    }

    // ---- Unidades de medida
    @GetMapping("/unidades-medida")
    public Pagina<UnidadeMedidaSaida> listarUnidades(@RequestParam(required = false) String termo,
                                                     @RequestParam(required = false) Situacao situacao,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     @RequestParam(required = false) String sort) {
        return servico.listarUnidades(termo, situacao, page, size, sort);
    }

    @GetMapping("/unidades-medida/{id}")
    public ResponseEntity<UnidadeMedidaSaida> obterUnidade(@PathVariable UUID id) {
        UnidadeMedidaSaida s = servico.obterUnidade(id);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/unidades-medida")
    public ResponseEntity<UnidadeMedidaSaida> criarUnidade(@Valid @RequestBody UnidadeMedidaEntrada e) {
        UnidadeMedidaSaida s = servico.criarUnidade(e);
        return Respostas.criado("/internal/v1/unidades-medida", s.id(), s, s.version());
    }

    @PutMapping("/unidades-medida/{id}")
    public ResponseEntity<UnidadeMedidaSaida> alterarUnidade(@PathVariable UUID id,
                                                             @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                             @Valid @RequestBody UnidadeMedidaEntrada e) {
        UnidadeMedidaSaida s = servico.alterarUnidade(id, ifMatch, e);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/unidades-medida/{id}/desativacao")
    public UnidadeMedidaSaida desativarUnidade(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacaoUnidade(id, false, e.justificativa());
    }

    @PostMapping("/unidades-medida/{id}/reativacao")
    public UnidadeMedidaSaida reativarUnidade(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacaoUnidade(id, true, e.justificativa());
    }

    @GetMapping("/unidades-medida/{id}/historico")
    public List<HistoricoDto> historicoUnidade(@PathVariable UUID id) {
        Suporte.exigirLeitura();
        return historico.listar("unidade-medida", id);
    }

    // ---- Fontes de recursos
    @GetMapping("/fontes-recurso")
    public Pagina<FonteRecursoSaida> listarFontes(@RequestParam(required = false) String termo,
                                                  @RequestParam(required = false) Situacao situacao,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort) {
        return servico.listarFontes(termo, situacao, page, size, sort);
    }

    @GetMapping("/fontes-recurso/{id}")
    public ResponseEntity<FonteRecursoSaida> obterFonte(@PathVariable UUID id) {
        FonteRecursoSaida s = servico.obterFonte(id);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/fontes-recurso")
    public ResponseEntity<FonteRecursoSaida> criarFonte(@Valid @RequestBody FonteRecursoEntrada e) {
        FonteRecursoSaida s = servico.criarFonte(e);
        return Respostas.criado("/internal/v1/fontes-recurso", s.id(), s, s.version());
    }

    @PutMapping("/fontes-recurso/{id}")
    public ResponseEntity<FonteRecursoSaida> alterarFonte(@PathVariable UUID id,
                                                          @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                          @Valid @RequestBody FonteRecursoEntrada e) {
        FonteRecursoSaida s = servico.alterarFonte(id, ifMatch, e);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/fontes-recurso/{id}/desativacao")
    public FonteRecursoSaida desativarFonte(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacaoFonte(id, false, e.justificativa());
    }

    @PostMapping("/fontes-recurso/{id}/reativacao")
    public FonteRecursoSaida reativarFonte(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacaoFonte(id, true, e.justificativa());
    }

    @GetMapping("/fontes-recurso/{id}/historico")
    public List<HistoricoDto> historicoFonte(@PathVariable UUID id) {
        Suporte.exigirLeitura();
        return historico.listar("fonte-recurso", id);
    }

    // ---- Serviços
    @GetMapping("/servicos")
    public Pagina<ServicoSaida> listarServicos(@RequestParam(required = false) String termo,
                                               @RequestParam(required = false) Situacao situacao,
                                               @RequestParam(required = false) Servico.Tipo tipo,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        return servico.listarServicos(termo, situacao, tipo, page, size, sort);
    }

    @GetMapping("/servicos/{id}")
    public ResponseEntity<ServicoSaida> obterServico(@PathVariable UUID id) {
        ServicoSaida s = servico.obterServico(id);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/servicos")
    public ResponseEntity<ServicoSaida> criarServico(@Valid @RequestBody ServicoEntrada e) {
        ServicoSaida s = servico.criarServico(e);
        return Respostas.criado("/internal/v1/servicos", s.id(), s, s.version());
    }

    @PutMapping("/servicos/{id}")
    public ResponseEntity<ServicoSaida> alterarServico(@PathVariable UUID id,
                                                       @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                       @Valid @RequestBody ServicoEntrada e) {
        ServicoSaida s = servico.alterarServico(id, ifMatch, e);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/servicos/{id}/desativacao")
    public ServicoSaida desativarServico(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacaoServico(id, false, e.justificativa());
    }

    @PostMapping("/servicos/{id}/reativacao")
    public ServicoSaida reativarServico(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacaoServico(id, true, e.justificativa());
    }

    @GetMapping("/servicos/{id}/historico")
    public List<HistoricoDto> historicoServico(@PathVariable UUID id) {
        Suporte.exigirLeitura();
        return historico.listar("servico", id);
    }
}
