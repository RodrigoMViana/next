package br.com.subti.next.configuracao.api;

import static br.com.subti.next.configuracao.api.Respostas.IF_MATCH;

import br.com.subti.next.configuracao.application.ContratadaService;
import br.com.subti.next.configuracao.application.HistoricoService;
import br.com.subti.next.configuracao.application.Pagina;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.MudancaSituacao;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.AlteracaoEntrada;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.AlteracaoSaida;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.Anexo;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.ContratadaEntrada;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.ContratadaSaida;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.EncerramentoMembro;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.MembroEntrada;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.MembroSaida;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.ValidacaoCnpj;
import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.domain.model.Situacao;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** API interna: contratadas, equipe e alterações contratuais (N02). */
@RestController
@RequestMapping("/internal/v1/contratadas")
@Tag(name = "Contratadas", description = "N02 - CNPJ numérico e alfanumérico, equipe e alterações contratuais")
public class ContratadaController {

    private final ContratadaService servico;
    private final HistoricoService historico;

    public ContratadaController(ContratadaService servico, HistoricoService historico) {
        this.servico = servico;
        this.historico = historico;
    }

    /** Valida um CNPJ (com ou sem máscara) e devolve a mensagem do campo. */
    @GetMapping("/validacao-cnpj")
    public ValidacaoCnpj validarCnpj(@RequestParam String valor) {
        return servico.validarCnpj(valor);
    }

    @GetMapping
    public Pagina<ContratadaSaida> listar(@RequestParam(required = false) String termo,
                                          @RequestParam(required = false) Situacao situacao,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String sort) {
        return servico.listar(termo, situacao, page, size, sort);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContratadaSaida> obter(@PathVariable UUID id) {
        ContratadaSaida s = servico.obter(id);
        return Respostas.ok(s, s.version());
    }

    @PostMapping
    public ResponseEntity<ContratadaSaida> criar(@Valid @RequestBody ContratadaEntrada e) {
        ContratadaSaida s = servico.criar(e);
        return Respostas.criado("/internal/v1/contratadas", s.id(), s, s.version());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContratadaSaida> alterar(@PathVariable UUID id,
                                                   @RequestHeader(value = IF_MATCH, required = false) String ifMatch,
                                                   @Valid @RequestBody ContratadaEntrada e) {
        ContratadaSaida s = servico.alterar(id, ifMatch, e);
        return Respostas.ok(s, s.version());
    }

    @PostMapping("/{id}/desativacao")
    public ContratadaSaida desativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, false, e.justificativa());
    }

    @PostMapping("/{id}/reativacao")
    public ContratadaSaida reativar(@PathVariable UUID id, @Valid @RequestBody MudancaSituacao e) {
        return servico.mudarSituacao(id, true, e.justificativa());
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoDto> historico(@PathVariable UUID id) {
        servico.obter(id);
        return historico.listar("contratada", id);
    }

    // ---- Equipe (RF07)
    @GetMapping("/{id}/equipe")
    public List<MembroSaida> equipe(@PathVariable UUID id) {
        return servico.listarEquipe(id);
    }

    @PostMapping("/{id}/equipe")
    public ResponseEntity<MembroSaida> adicionarMembro(@PathVariable UUID id, @Valid @RequestBody MembroEntrada e) {
        MembroSaida m = servico.adicionarMembro(id, e);
        return Respostas.criado("/internal/v1/contratadas/" + id + "/equipe", m.id(), m, m.version());
    }

    @PostMapping("/{id}/equipe/{membroId}/encerramento")
    public MembroSaida encerrarMembro(@PathVariable UUID id, @PathVariable UUID membroId,
                                      @Valid @RequestBody EncerramentoMembro e) {
        return servico.encerrarMembro(id, membroId, e.fim());
    }

    // ---- Alterações contratuais (RF08)
    @GetMapping("/{id}/alteracoes")
    public List<AlteracaoSaida> alteracoes(@PathVariable UUID id) {
        return servico.listarAlteracoes(id);
    }

    @PostMapping(value = "/{id}/alteracoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlteracaoSaida> registrarAlteracao(@PathVariable UUID id,
                                                             @RequestParam LocalDate dataAlteracao,
                                                             @RequestParam String tipo,
                                                             @RequestParam String descricao,
                                                             @RequestPart(required = false) MultipartFile anexo)
            throws IOException {
        Anexo a = anexo == null || anexo.isEmpty() ? null
                : new Anexo(nomeSeguro(anexo.getOriginalFilename()), anexo.getContentType(), anexo.getBytes());
        AlteracaoSaida s = servico.registrarAlteracao(id, new AlteracaoEntrada(dataAlteracao, tipo, descricao), a);
        return Respostas.criado("/internal/v1/contratadas/" + id + "/alteracoes", s.id(), s, 0);
    }

    @PostMapping(value = "/{id}/alteracoes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AlteracaoSaida> registrarAlteracaoSemAnexo(@PathVariable UUID id,
                                                                     @Valid @RequestBody AlteracaoEntrada e) {
        AlteracaoSaida s = servico.registrarAlteracao(id, e, null);
        return Respostas.criado("/internal/v1/contratadas/" + id + "/alteracoes", s.id(), s, 0);
    }

    @GetMapping("/{id}/alteracoes/{alteracaoId}/anexo")
    public ResponseEntity<byte[]> anexo(@PathVariable UUID id, @PathVariable UUID alteracaoId) {
        Anexo a = servico.baixarAnexo(id, alteracaoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(a.nome(),
                        java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(a.tipo())).body(a.conteudo());
    }

    private static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) {
            return "anexo";
        }
        String base = nome.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
