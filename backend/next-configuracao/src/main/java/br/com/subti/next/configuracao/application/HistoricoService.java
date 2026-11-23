package br.com.subti.next.configuracao.application;

import br.com.subti.next.configuracao.application.dto.HistoricoDto;
import br.com.subti.next.configuracao.domain.model.HistoricoAlteracao;
import br.com.subti.next.configuracao.domain.model.Rastreavel;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.repositorio.HistoricoAlteracaoRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o histórico de alterações (RF16) e os registros de governança (RNF04). Somente inclusão.
 */
@Service
public class HistoricoService {

    public static final String CRIACAO = "CRIACAO";
    public static final String ALTERACAO = "ALTERACAO";
    public static final String DESATIVACAO = "DESATIVACAO";
    public static final String REATIVACAO = "REATIVACAO";

    private static final int LIMITE = 4000;

    private final HistoricoAlteracaoRepository repositorio;

    public HistoricoService(HistoricoAlteracaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void criacao(Rastreavel entidade) {
        String resumo = entidade.snapshot().entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(" | "));
        gravar(entidade.tipoEntidade(), entidade.getId(), CRIACAO, null, null, resumo, null);
    }

    /** Uma linha por campo alterado, com valor anterior e novo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void alteracao(Rastreavel entidade, Map<String, String> antes, String justificativa) {
        Map<String, String> depois = entidade.snapshot();
        Set<String> campos = new HashSet<>(antes.keySet());
        campos.addAll(depois.keySet());
        campos.stream().sorted().forEach(campo -> {
            String a = antes.get(campo);
            String d = depois.get(campo);
            if (!Objects.equals(vazio(a), vazio(d))) {
                gravar(entidade.tipoEntidade(), entidade.getId(), ALTERACAO, campo, a, d, justificativa);
            }
        });
    }

    /** Ação de governança (desativação, aditivo, substituição de fiscal, decisão de SLA...). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acao(String entidade, UUID id, String acao, String descricao, String justificativa) {
        gravar(entidade, id, acao, null, null, descricao, justificativa);
    }

    @Transactional(readOnly = true)
    public List<HistoricoDto> listar(String entidade, UUID id) {
        return repositorio.findByEntidadeAndEntidadeIdOrderByOcorridoEmDesc(entidade, id.toString()).stream()
                .map(HistoricoDto::de).toList();
    }

    private void gravar(String entidade, UUID id, String acao, String campo, String anterior, String novo,
                        String justificativa) {
        UsuarioAtual u = UsuarioAtual.get();
        HistoricoAlteracao h = new HistoricoAlteracao();
        h.setEntidade(entidade);
        h.setEntidadeId(id.toString());
        h.setAcao(acao);
        h.setCampo(campo);
        h.setValorAnterior(cortar(anterior));
        h.setValorNovo(cortar(novo));
        h.setJustificativa(cortar(justificativa));
        h.setUsuarioId(u.id());
        h.setUsuarioNome(u.nome());
        h.setOcorridoEm(Instant.now());
        h.setCorrelationId(MDC.get("correlationId"));
        repositorio.save(h);
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String cortar(String s) {
        return s == null || s.length() <= LIMITE ? s : s.substring(0, LIMITE - 3) + "...";
    }
}
