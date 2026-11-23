package br.com.subti.next.bff.auditoria;

import br.com.subti.next.bff.seguranca.LimiteTaxa;
import br.com.subti.next.bff.seguranca.Rbac;
import br.com.subti.next.bff.seguranca.Sessao;
import br.com.subti.next.bff.seguranca.UsuarioSessao;
import br.com.subti.next.bff.web.ErroBff;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auditoria (N05): consulta da trilha de acesso a dados pessoais (gravada pelo BFF) e exportações
 * assíncronas. Os registros de governança vêm do módulo por /api/v1/configuracao/auditoria/governanca.
 * Restrito a Administrador e Gestor de TI; o próprio acesso à consulta é registrado (RN23).
 */
@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {

    public record Pagina<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record PedidoExportacao(@NotBlank @Pattern(regexp = "ACESSOS|GOVERNANCA") String tipo,
                                   Map<String, String> filtros) {
    }

    private final TrilhaRepositorio trilha;
    private final ExportacaoServico exportacoes;
    private final LimiteTaxa limite;
    private final int exportacoesPorMinuto;

    public AuditoriaController(TrilhaRepositorio trilha, ExportacaoServico exportacoes, LimiteTaxa limite,
                               @Value("${next.seguranca.limites.exportacoes-por-minuto:3}") int exportacoesPorMinuto) {
        this.trilha = trilha;
        this.exportacoes = exportacoes;
        this.limite = limite;
        this.exportacoesPorMinuto = exportacoesPorMinuto;
    }

    @GetMapping("/acessos")
    public Pagina<TrilhaRepositorio.Registro> acessos(@RequestParam(required = false) String usuarioId,
                                                      @RequestParam(required = false) String modulo,
                                                      @RequestParam(required = false) String recurso,
                                                      @RequestParam(required = false) String acao,
                                                      @RequestParam(required = false) Instant de,
                                                      @RequestParam(required = false) Instant ate,
                                                      @RequestParam(required = false) Boolean dadoPessoal,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        gestao();
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(page, 0);
        var filtro = new TrilhaRepositorio.Filtro(usuarioId, modulo, recurso, acao, de, ate, dadoPessoal);
        long total = trilha.contar(filtro);
        return new Pagina<>(trilha.pesquisar(filtro, p, s), p, s, total, (int) Math.ceil(total / (double) s));
    }

    @PostMapping("/exportacoes")
    public ResponseEntity<Map<String, String>> exportar(@Valid @RequestBody PedidoExportacao e) {
        UsuarioSessao u = gestao();
        if (!limite.permitir("exp:" + u.id(), exportacoesPorMinuto)) {
            throw new ErroBff(429, "limite-de-exportacoes", "Muitas exportações", "Aguarde um minuto para solicitar outra.");
        }
        Map<String, String> filtros = e.filtros() == null ? Map.of() : new HashMap<>(e.filtros());
        filtros.keySet().retainAll(List.of("usuarioId", "modulo", "recurso", "acao", "entidade", "entidadeId", "de", "ate"));
        String id = exportacoes.registrar(u, e.tipo(), filtros);
        exportacoes.processar(id, u, e.tipo(), filtros);
        return ResponseEntity.accepted().body(Map.of("id", id, "situacao", "PROCESSANDO",
                "mensagem", "Exportação em processamento. Você será avisado quando o arquivo estiver pronto."));
    }

    @GetMapping("/exportacoes")
    public List<ExportacaoServico.Exportacao> minhas() {
        return exportacoes.listar(gestao());
    }

    /** Gera o link temporário (token de uso do próprio solicitante, válido até a expiração). */
    @PostMapping("/exportacoes/{id}/link")
    public Map<String, String> link(@PathVariable String id) {
        UsuarioSessao u = gestao();
        String token = exportacoes.gerarLink(u, id).orElseThrow(() -> new ErroBff(404, "exportacao-indisponivel",
                "Exportação indisponível", "A exportação não existe, não terminou ou o link expirou."));
        return Map.of("url", "/api/v1/auditoria/exportacoes/" + id + "/arquivo?token=" + token);
    }

    @GetMapping("/exportacoes/{id}/arquivo")
    public ResponseEntity<byte[]> arquivo(@PathVariable String id, @RequestParam(required = false) String token) {
        UsuarioSessao u = gestao();
        var a = exportacoes.baixar(u, id, token).orElseThrow(() -> new ErroBff(404, "link-invalido",
                "Link inválido ou expirado", "Gere um novo link na lista de exportações."));
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(a.nome()).build().toString())
                .header("Cache-Control", "no-store").body(a.conteudo());
    }

    private static UsuarioSessao gestao() {
        UsuarioSessao u = Sessao.usuario().orElseThrow(() -> new ErroBff(401, "nao-autenticado", "Sessão inexistente",
                "Entre para continuar."));
        if (!u.temAlgum(Rbac.GESTAO)) {
            throw new ErroBff(403, "acesso-negado", "Acesso negado", "A auditoria é restrita a Administrador e Gestor de TI.");
        }
        return u;
    }
}
