package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.erroCampo;
import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.exigirLeitura;
import static br.com.subti.next.configuracao.application.Suporte.regra;
import static br.com.subti.next.configuracao.application.Suporte.termo;
import static br.com.subti.next.configuracao.application.Suporte.texto;
import static br.com.subti.next.configuracao.application.Suporte.verificarVersao;

import br.com.subti.next.configuracao.application.dto.ApoioDtos.FonteRecursoEntrada;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.FonteRecursoSaida;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.ServicoEntrada;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.ServicoSaida;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.UnidadeMedidaEntrada;
import br.com.subti.next.configuracao.application.dto.ApoioDtos.UnidadeMedidaSaida;
import br.com.subti.next.configuracao.domain.excecao.ConflitoException;
import br.com.subti.next.configuracao.domain.excecao.RecursoNaoEncontradoException;
import br.com.subti.next.configuracao.domain.model.CadastroComSituacao;
import br.com.subti.next.configuracao.domain.model.FonteRecurso;
import br.com.subti.next.configuracao.domain.model.Servico;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.UnidadeMedida;
import br.com.subti.next.configuracao.domain.repositorio.ContratoRepository;
import br.com.subti.next.configuracao.domain.repositorio.FonteRecursoRepository;
import br.com.subti.next.configuracao.domain.repositorio.ServicoRepository;
import br.com.subti.next.configuracao.domain.repositorio.UnidadeMedidaRepository;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de unidade de medida (N07), fonte de recursos (N08) e serviço (N09). */
@Service
@Transactional
public class CadastroApoioService {

    private static final Map<String, String> ORDEM_UNIDADE = Map.of("sigla", "sigla", "nome", "nome", "situacao", "situacao");
    private static final Map<String, String> ORDEM_FONTE = Map.of("codigo", "codigo", "nome", "nome", "situacao", "situacao");
    private static final Map<String, String> ORDEM_SERVICO = Map.of("codigo", "codigo", "nome", "nome", "tipo", "tipo",
            "situacao", "situacao");

    private final UnidadeMedidaRepository unidades;
    private final FonteRecursoRepository fontes;
    private final ServicoRepository servicos;
    private final ContratoRepository contratos;
    private final HistoricoService historico;
    private final OutboxService outbox;

    public CadastroApoioService(UnidadeMedidaRepository unidades, FonteRecursoRepository fontes,
                                ServicoRepository servicos, ContratoRepository contratos, HistoricoService historico,
                                OutboxService outbox) {
        this.unidades = unidades;
        this.fontes = fontes;
        this.servicos = servicos;
        this.contratos = contratos;
        this.historico = historico;
        this.outbox = outbox;
    }

    // ---------------------------------------------------------------- Unidade de medida (N07)

    @Transactional(readOnly = true)
    public Pagina<UnidadeMedidaSaida> listarUnidades(String busca, Situacao situacao, Integer page, Integer size, String sort) {
        exigirLeitura();
        return Pagina.de(unidades.pesquisar(termo(busca), situacao,
                Pagina.pageable(page, size, sort, ORDEM_UNIDADE, "sigla")), UnidadeMedidaSaida::de);
    }

    @Transactional(readOnly = true)
    public UnidadeMedidaSaida obterUnidade(UUID id) {
        exigirLeitura();
        return UnidadeMedidaSaida.de(unidade(id));
    }

    public UnidadeMedidaSaida criarUnidade(UnidadeMedidaEntrada e) {
        exigirGestao();
        UnidadeMedida u = new UnidadeMedida();
        preencher(u, e);
        unidades.save(u);
        historico.criacao(u);
        return UnidadeMedidaSaida.de(u);
    }

    public UnidadeMedidaSaida alterarUnidade(UUID id, String ifMatch, UnidadeMedidaEntrada e) {
        exigirGestao();
        UnidadeMedida u = unidade(id);
        verificarVersao(u, ifMatch);
        var antes = u.snapshot();
        preencher(u, e);
        unidades.saveAndFlush(u);
        historico.alteracao(u, antes, null);
        return UnidadeMedidaSaida.de(u);
    }

    public UnidadeMedidaSaida mudarSituacaoUnidade(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        UnidadeMedida u = unidade(id);
        if (!ativar) {
            long emUso = servicos.countByUnidadeMedidaIdAndSituacao(id, Situacao.ATIVO)
                    + contratos.contarComUnidade(id, Situacao.ATIVO);
            exigirJustificativaSeEmUso(emUso, justificativa, "A unidade de medida");
        }
        mudarSituacao(u, ativar, justificativa);
        unidades.saveAndFlush(u);
        return UnidadeMedidaSaida.de(u);
    }

    private void preencher(UnidadeMedida u, UnidadeMedidaEntrada e) {
        String sigla = e.sigla().trim().toUpperCase(Locale.ROOT);
        if (unidades.existsBySiglaIgnoreCaseAndIdNot(sigla, u.getId())) {
            throw new ConflitoException("sigla-duplicada", "Sigla já cadastrada", "sigla",
                    "Já existe uma unidade de medida com a sigla " + sigla + ".");
        }
        u.setSigla(sigla);
        u.setNome(e.nome().trim());
        u.setDescricao(texto(e.descricao()));
    }

    private UnidadeMedida unidade(UUID id) {
        return unidades.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Unidade de medida", id));
    }

    // ---------------------------------------------------------------- Fonte de recursos (N08)

    @Transactional(readOnly = true)
    public Pagina<FonteRecursoSaida> listarFontes(String busca, Situacao situacao, Integer page, Integer size, String sort) {
        exigirLeitura();
        return Pagina.de(fontes.pesquisar(termo(busca), situacao,
                Pagina.pageable(page, size, sort, ORDEM_FONTE, "codigo")), FonteRecursoSaida::de);
    }

    @Transactional(readOnly = true)
    public FonteRecursoSaida obterFonte(UUID id) {
        exigirLeitura();
        return FonteRecursoSaida.de(fonte(id));
    }

    public FonteRecursoSaida criarFonte(FonteRecursoEntrada e) {
        exigirGestao();
        FonteRecurso f = new FonteRecurso();
        preencher(f, e);
        fontes.save(f);
        historico.criacao(f);
        return FonteRecursoSaida.de(f);
    }

    public FonteRecursoSaida alterarFonte(UUID id, String ifMatch, FonteRecursoEntrada e) {
        exigirGestao();
        FonteRecurso f = fonte(id);
        verificarVersao(f, ifMatch);
        var antes = f.snapshot();
        preencher(f, e);
        fontes.saveAndFlush(f);
        historico.alteracao(f, antes, null);
        return FonteRecursoSaida.de(f);
    }

    public FonteRecursoSaida mudarSituacaoFonte(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        FonteRecurso f = fonte(id);
        if (!ativar) {
            exigirJustificativaSeEmUso(contratos.contarComFonte(id, Situacao.ATIVO), justificativa, "A fonte de recursos");
        }
        mudarSituacao(f, ativar, justificativa);
        fontes.saveAndFlush(f);
        return FonteRecursoSaida.de(f);
    }

    private void preencher(FonteRecurso f, FonteRecursoEntrada e) {
        String codigo = e.codigo().trim().toUpperCase(Locale.ROOT);
        if (fontes.existsByCodigoIgnoreCaseAndIdNot(codigo, f.getId())) {
            throw new ConflitoException("codigo-duplicado", "Código já cadastrado", "codigo",
                    "Já existe uma fonte de recursos com o código " + codigo + ".");
        }
        f.setCodigo(codigo);
        f.setNome(e.nome().trim());
        f.setDescricao(texto(e.descricao()));
    }

    FonteRecurso fonte(UUID id) {
        return fontes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Fonte de recursos", id));
    }

    // ---------------------------------------------------------------- Serviço (N09)

    @Transactional(readOnly = true)
    public Pagina<ServicoSaida> listarServicos(String busca, Situacao situacao, Servico.Tipo tipo, Integer page,
                                               Integer size, String sort) {
        exigirLeitura();
        return Pagina.de(servicos.pesquisar(termo(busca), situacao, tipo,
                Pagina.pageable(page, size, sort, ORDEM_SERVICO, "codigo")), ServicoSaida::de);
    }

    @Transactional(readOnly = true)
    public ServicoSaida obterServico(UUID id) {
        exigirLeitura();
        return ServicoSaida.de(servico(id));
    }

    public ServicoSaida criarServico(ServicoEntrada e) {
        exigirGestao();
        Servico s = new Servico();
        preencher(s, e);
        servicos.save(s);
        historico.criacao(s);
        return ServicoSaida.de(s);
    }

    public ServicoSaida alterarServico(UUID id, String ifMatch, ServicoEntrada e) {
        exigirGestao();
        Servico s = servico(id);
        verificarVersao(s, ifMatch);
        var antes = s.snapshot();
        preencher(s, e);
        servicos.saveAndFlush(s);
        historico.alteracao(s, antes, null);
        return ServicoSaida.de(s);
    }

    public ServicoSaida mudarSituacaoServico(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        Servico s = servico(id);
        if (!ativar) {
            exigirJustificativaSeEmUso(contratos.contarComServico(id, Situacao.ATIVO), justificativa, "O serviço");
        } else if (!s.getUnidadeMedida().ativo()) {
            throw erroCampo("unidadeMedidaId", "Reative a unidade de medida " + s.getUnidadeMedida().getSigla() + " antes do serviço.");
        }
        mudarSituacao(s, ativar, justificativa);
        servicos.saveAndFlush(s);
        return ServicoSaida.de(s);
    }

    private void preencher(Servico s, ServicoEntrada e) {
        String codigo = e.codigo().trim().toUpperCase(Locale.ROOT);
        if (servicos.existsByCodigoIgnoreCaseAndIdNot(codigo, s.getId())) {
            throw new ConflitoException("codigo-duplicado", "Código já cadastrado", "codigo",
                    "Já existe um serviço com o código " + codigo + ".");
        }
        UnidadeMedida u = unidade(e.unidadeMedidaId());
        if (!u.ativo() && (s.getUnidadeMedida() == null || !s.getUnidadeMedida().getId().equals(u.getId()))) {
            throw erroCampo("unidadeMedidaId", "A unidade de medida " + u.getSigla() + " está desativada.");
        }
        s.setCodigo(codigo);
        s.setNome(e.nome().trim());
        s.setDescricao(texto(e.descricao()));
        s.setTipo(e.tipo());
        s.setUnidadeMedida(u);
    }

    Servico servico(UUID id) {
        return servicos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Serviço", id));
    }

    // ---------------------------------------------------------------- comum

    private void exigirJustificativaSeEmUso(long emUso, String justificativa, String rotulo) {
        if (emUso > 0 && texto(justificativa) == null) {
            throw erroCampo("justificativa", rotulo + " está em uso por " + emUso
                    + " cadastro(s) ativo(s). Informe a justificativa da desativação.");
        }
    }

    private void mudarSituacao(CadastroComSituacao c, boolean ativar, String justificativa) {
        regra("situacao", () -> {
            if (ativar) {
                c.reativar(texto(justificativa));
            } else {
                c.desativar(texto(justificativa));
            }
        });
        historico.acao(c.tipoEntidade(), c.getId(), ativar ? HistoricoService.REATIVACAO : HistoricoService.DESATIVACAO,
                ativar ? "Situação: ATIVO" : "Situação: DESATIVADO", texto(justificativa));
        outbox.registrar("configuracao." + c.tipoEntidade() + (ativar ? ".reativado" : ".desativado"),
                Map.of("id", c.getId().toString()));
    }
}
