package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.erroCampo;
import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.exigirLeitura;
import static br.com.subti.next.configuracao.application.Suporte.hoje;
import static br.com.subti.next.configuracao.application.Suporte.regra;
import static br.com.subti.next.configuracao.application.Suporte.termo;
import static br.com.subti.next.configuracao.application.Suporte.texto;
import static br.com.subti.next.configuracao.application.Suporte.usuario;
import static br.com.subti.next.configuracao.application.Suporte.verificarVersao;

import br.com.subti.next.configuracao.application.dto.ContratoDtos.AditivoEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ContratoDetalhe;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ContratoEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ContratoResumo;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.EncerramentoFiscal;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.FiscalEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.ItemEntrada;
import br.com.subti.next.configuracao.application.dto.ContratoDtos.SubstituicaoFiscal;
import br.com.subti.next.configuracao.domain.excecao.ConflitoException;
import br.com.subti.next.configuracao.domain.excecao.NegocioException;
import br.com.subti.next.configuracao.domain.excecao.RecursoNaoEncontradoException;
import br.com.subti.next.configuracao.domain.model.Calendario;
import br.com.subti.next.configuracao.domain.model.Contratada;
import br.com.subti.next.configuracao.domain.model.Contrato;
import br.com.subti.next.configuracao.domain.model.ContratoAditivo;
import br.com.subti.next.configuracao.domain.model.ContratoFiscal;
import br.com.subti.next.configuracao.domain.model.ContratoItem;
import br.com.subti.next.configuracao.domain.model.ContratoSistema;
import br.com.subti.next.configuracao.domain.model.FonteRecurso;
import br.com.subti.next.configuracao.domain.model.Servico;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.porta.CoreGateway;
import br.com.subti.next.configuracao.domain.porta.PortfolioGateway;
import br.com.subti.next.configuracao.domain.regra.ContratoCalculos;
import br.com.subti.next.configuracao.domain.regra.Fiscalizacao;
import br.com.subti.next.configuracao.domain.repositorio.ContratoAditivoRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratoFiscalRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do contrato (N01, RF01 a RF05, RN01 a RN06). */
@Service
@Transactional
public class ContratoService {

    private static final Map<String, String> ORDEM = Map.of("numero", "numero", "vigenciaInicio", "vigenciaInicio",
            "vigenciaFim", "vigenciaFim", "situacao", "situacao", "contratada", "contratada.razaoSocial");

    private final ContratoRepository contratos;
    private final ContratoAditivoRepository aditivos;
    private final ContratoFiscalRepository fiscais;
    private final ContratadaService contratadaService;
    private final CalendarioService calendarioService;
    private final CadastroApoioService apoio;
    private final PortfolioGateway portfolio;
    private final CoreGateway core;
    private final HistoricoService historico;
    private final OutboxService outbox;

    public ContratoService(ContratoRepository contratos, ContratoAditivoRepository aditivos,
                           ContratoFiscalRepository fiscais, ContratadaService contratadaService,
                           CalendarioService calendarioService, CadastroApoioService apoio, PortfolioGateway portfolio,
                           CoreGateway core, HistoricoService historico, OutboxService outbox) {
        this.contratos = contratos;
        this.aditivos = aditivos;
        this.fiscais = fiscais;
        this.contratadaService = contratadaService;
        this.calendarioService = calendarioService;
        this.apoio = apoio;
        this.portfolio = portfolio;
        this.core = core;
        this.historico = historico;
        this.outbox = outbox;
    }

    /** RF01: filtros por situação, contratada e vigência, com paginação e ordenação. */
    @Transactional(readOnly = true)
    public Pagina<ContratoResumo> listar(String busca, Situacao situacao, UUID contratadaId, LocalDate vigenteEm,
                                         Integer page, Integer size, String sort) {
        exigirLeitura();
        UsuarioAtual u = usuario();
        UUID filtroContratada = contratadaId;
        if (u.restritoAFabrica()) {
            filtroContratada = ContratadaService.fabricaDoUsuario(u);
        }
        LocalDate hoje = hoje();
        return Pagina.de(contratos.pesquisar(termo(busca), situacao, filtroContratada, vigenteEm,
                Pagina.pageable(page, size, sort, ORDEM, "numero")), c -> ContratoResumo.de(c, hoje));
    }

    @Transactional(readOnly = true)
    public ContratoDetalhe obter(UUID id) {
        exigirLeitura();
        return ContratoDetalhe.de(contratoVisivel(id), hoje());
    }

    /** RF02 + RN06: o contrato nasce com itens e ao menos um fiscal com designação vigente. */
    public ContratoDetalhe criar(ContratoEntrada e) {
        exigirGestao();
        Contrato c = new Contrato();
        preencher(c, e);
        List<FiscalEntrada> iniciais = e.fiscais() == null ? List.of() : e.fiscais();
        if (iniciais.isEmpty()) {
            throw erroCampo("fiscais", "Designe ao menos um fiscal para o contrato (comissão de fiscalização).");
        }
        contratos.saveAndFlush(c);
        for (FiscalEntrada f : iniciais) {
            c.getFiscais().add(novoFiscal(c, f.usuarioId(), f.tipoFiscal(), f.inicio(), f.fim()));
        }
        LocalDate referencia = c.getVigenciaInicio().isAfter(hoje()) ? c.getVigenciaInicio() : hoje();
        if (c.fiscaisVigentes(referencia).isEmpty()) {
            throw erroCampo("fiscais", "Ao menos um fiscal deve ter designação vigente no início do contrato.");
        }
        historico.criacao(c);
        outbox.registrar("configuracao.contrato.criado", Map.of("contratoId", c.getId().toString(),
                "numero", c.getNumero(), "contratadaId", c.getContratada().getId().toString()));
        return ContratoDetalhe.de(c, hoje());
    }

    public ContratoDetalhe alterar(UUID id, String ifMatch, ContratoEntrada e) {
        exigirGestao();
        Contrato c = contrato(id);
        verificarVersao(c, ifMatch);
        if (!c.ativo()) {
            throw erroCampo("situacao", "Reative o contrato antes de alterá-lo.");
        }
        var antes = c.snapshot();
        preencher(c, e);
        contratos.saveAndFlush(c);
        historico.alteracao(c, antes, null);
        outbox.registrar("configuracao.contrato.alterado", Map.of("contratoId", c.getId().toString()));
        return ContratoDetalhe.de(c, hoje());
    }

    /** RF05: desativação e reativação sempre com justificativa, sem exclusão física. */
    public ContratoDetalhe mudarSituacao(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        Contrato c = contrato(id);
        if (texto(justificativa) == null) {
            throw erroCampo("justificativa", "Informe a justificativa.");
        }
        if (ativar && !c.getContratada().ativo()) {
            throw erroCampo("contratadaId", "A contratada do contrato está desativada.");
        }
        regra("situacao", () -> {
            if (ativar) {
                c.reativar(justificativa.trim());
            } else {
                c.desativar(justificativa.trim());
            }
        });
        contratos.saveAndFlush(c);
        historico.acao(c.tipoEntidade(), c.getId(), ativar ? HistoricoService.REATIVACAO : HistoricoService.DESATIVACAO,
                "Situação: " + c.getSituacao(), justificativa.trim());
        outbox.registrar(ativar ? "configuracao.contrato.reativado" : "configuracao.contrato.desativado",
                Map.of("contratoId", c.getId().toString()));
        return ContratoDetalhe.de(c, hoje());
    }

    private void preencher(Contrato c, ContratoEntrada e) {
        String numero = e.numero().trim().toUpperCase(java.util.Locale.ROOT);
        if (contratos.existsByNumeroIgnoreCaseAndIdNot(numero, c.getId())) {
            throw new ConflitoException("numero-duplicado", "Número já cadastrado", "numero",
                    "Já existe um contrato com o número " + numero + ".");
        }
        regra("vigenciaFim", () -> ContratoCalculos.validarVigencia(e.vigenciaInicio(), e.vigenciaFim()));

        Contratada contratada = contratadaService.contratada(e.contratadaId());
        if (!contratada.ativo() && (c.getContratada() == null || !c.getContratada().getId().equals(contratada.getId()))) {
            throw erroCampo("contratadaId", "A contratada está desativada.");
        }
        Calendario calendario = calendarioService.calendario(e.calendarioId());
        if (!calendario.ativo()) {
            throw erroCampo("calendarioId", "O calendário está desativado.");
        }

        Set<UUID> servicosVistos = new HashSet<>();
        List<ContratoItem> itens = new ArrayList<>();
        for (int i = 0; i < e.itens().size(); i++) {
            ItemEntrada item = e.itens().get(i);
            if (!servicosVistos.add(item.servicoId())) {
                throw erroCampo("itens[" + i + "].servicoId", "O serviço foi informado em mais de um item.");
            }
            Servico s = apoio.servico(item.servicoId());
            if (!s.ativo()) {
                throw erroCampo("itens[" + i + "].servicoId", "O serviço " + s.getCodigo() + " está desativado.");
            }
            int indice = i;
            itens.add(regra("itens[" + indice + "]", () -> new ContratoItem(s, item.volume(), item.valorUnitario())));
        }

        Set<FonteRecurso> fontes = new LinkedHashSet<>();
        for (UUID fid : e.fonteIds()) {
            FonteRecurso f = apoio.fonte(fid);
            if (!f.ativo() && !c.getFontes().contains(f)) {
                throw erroCampo("fonteIds", "A fonte de recursos " + f.getCodigo() + " está desativada.");
            }
            fontes.add(f);
        }
        if (fontes.isEmpty()) {
            throw erroCampo("fonteIds", "Informe ao menos uma fonte de recursos.");
        }

        Map<UUID, String> nomesAtuais = new HashMap<>();
        c.getSistemas().forEach(s -> nomesAtuais.put(s.getSistemaId(), s.getSistemaNome()));
        List<ContratoSistema> sistemas = new ArrayList<>();
        for (UUID sid : new LinkedHashSet<>(e.sistemaIds())) {
            String nome = portfolio.buscar(sid).map(PortfolioGateway.SistemaPortfolio::nome)
                    .orElseGet(() -> nomesAtuais.get(sid));
            if (nome == null) {
                throw erroCampo("sistemaIds", "Sistema " + sid + " não encontrado no Portfólio.");
            }
            sistemas.add(new ContratoSistema(sid, nome));
        }

        c.setNumero(numero);
        c.setObjeto(e.objeto().trim());
        c.setContratada(contratada);
        c.setCalendario(calendario);
        c.setVigenciaInicio(e.vigenciaInicio());
        c.setVigenciaFim(e.vigenciaFim());
        c.getItens().clear();
        c.getItens().addAll(itens);
        c.getFontes().clear();
        c.getFontes().addAll(fontes);
        sincronizarSistemas(c, sistemas);
        if (!c.getAditivos().isEmpty()) {
            regra("itens", c::valorEfetivo);
        }
    }

    private static void sincronizarSistemas(Contrato c, List<ContratoSistema> novos) {
        Set<UUID> ids = new HashSet<>();
        novos.forEach(n -> ids.add(n.getSistemaId()));
        c.getSistemas().removeIf(s -> !ids.contains(s.getSistemaId()));
        for (ContratoSistema n : novos) {
            c.getSistemas().stream().filter(s -> s.getSistemaId().equals(n.getSistemaId())).findFirst()
                    .ifPresentOrElse(s -> s.setSistemaNome(n.getSistemaNome()), () -> c.getSistemas().add(n));
        }
    }

    // ---------------------------------------------------------------- Aditivos (RF03, RN05)

    /** Registra aditivo sem sobrescrever os dados originais; vigência e valor efetivos são recalculados. */
    public ContratoDetalhe registrarAditivo(UUID contratoId, AditivoEntrada e) {
        exigirGestao();
        Contrato c = contrato(contratoId);
        if (!c.ativo()) {
            throw erroCampo("situacao", "Não é possível aditar um contrato desativado.");
        }
        if (!e.aditivoPrazo() && !e.aditivoValor()) {
            throw erroCampo("aditivoPrazo", "Marque aditivo de prazo, de valor ou ambos.");
        }
        if (aditivos.existsByContratoIdAndNumeroIgnoreCase(contratoId, e.numero().trim())) {
            throw new ConflitoException("aditivo-duplicado", "Aditivo já registrado", "numero",
                    "O contrato já tem um aditivo com o número " + e.numero().trim() + ".");
        }
        if (e.dataAssinatura().isBefore(c.getVigenciaInicio())) {
            throw erroCampo("dataAssinatura", "A assinatura do aditivo não pode ser anterior ao início da vigência.");
        }
        if (e.aditivoPrazo()) {
            regra("novaVigenciaFim", () -> ContratoCalculos.validarAditivoPrazo(c.getVigenciaInicio(),
                    c.vigenciaFimEfetiva(), e.novaVigenciaFim()));
        } else if (e.novaVigenciaFim() != null) {
            throw erroCampo("novaVigenciaFim", "Informe a nova vigência apenas em aditivo de prazo.");
        }
        if (e.aditivoValor()) {
            if (e.valorAcrescimo() == null || e.valorAcrescimo().signum() == 0) {
                throw erroCampo("valorAcrescimo", "Informe o valor do aditivo (negativo para supressão).");
            }
            BigDecimal novoEfetivo = c.valorEfetivo().add(e.valorAcrescimo());
            if (novoEfetivo.signum() < 0) {
                throw erroCampo("valorAcrescimo", "A supressão não pode tornar o valor do contrato negativo.");
            }
        } else if (e.valorAcrescimo() != null) {
            throw erroCampo("valorAcrescimo", "Informe valor apenas em aditivo de valor.");
        }
        LocalDate fimAnterior = c.vigenciaFimEfetiva();
        BigDecimal valorAnterior = c.valorEfetivo();
        ContratoAditivo a = new ContratoAditivo();
        a.setContratoId(contratoId);
        a.setNumero(e.numero().trim());
        a.setAditivoPrazo(e.aditivoPrazo());
        a.setAditivoValor(e.aditivoValor());
        a.setNovaVigenciaFim(e.aditivoPrazo() ? e.novaVigenciaFim() : null);
        a.setValorAcrescimo(e.aditivoValor() ? e.valorAcrescimo() : null);
        a.setDataAssinatura(e.dataAssinatura());
        a.setJustificativa(e.justificativa().trim());
        aditivos.save(a);
        c.getAditivos().add(a);
        String descricao = "Aditivo " + a.getNumero()
                + (a.isAditivoPrazo() ? " | vigência efetiva: " + fimAnterior + " -> " + c.vigenciaFimEfetiva() : "")
                + (a.isAditivoValor() ? " | valor efetivo: " + valorAnterior + " -> " + c.valorEfetivo() : "");
        historico.acao(c.tipoEntidade(), c.getId(), "ADITIVO", descricao, a.getJustificativa());
        outbox.registrar("configuracao.aditivo.registrado", Map.of("contratoId", c.getId().toString(),
                "aditivoId", a.getId().toString(), "vigenciaFimEfetiva", c.vigenciaFimEfetiva().toString(),
                "valorEfetivo", c.valorEfetivo().toPlainString()));
        return ContratoDetalhe.de(c, hoje());
    }

    // ---------------------------------------------------------------- Comissão de fiscalização (RF04, RN06)

    public ContratoDetalhe designarFiscal(UUID contratoId, FiscalEntrada e) {
        exigirGestao();
        Contrato c = contrato(contratoId);
        c.getFiscais().add(novoFiscal(c, e.usuarioId(), e.tipoFiscal(), e.inicio(), e.fim()));
        return ContratoDetalhe.de(c, hoje());
    }

    /** RN06: a substituição encerra a designação anterior e cria outra, com início e fim registrados. */
    public ContratoDetalhe substituirFiscal(UUID contratoId, UUID fiscalId, SubstituicaoFiscal e) {
        exigirGestao();
        Contrato c = contrato(contratoId);
        ContratoFiscal anterior = fiscal(c, fiscalId);
        if (anterior.getFim() != null && anterior.getFim().isBefore(hoje())) {
            throw erroCampo("fiscalId", "A designação já está encerrada.");
        }
        LocalDate fimAnterior = regra("inicio", () -> Fiscalizacao.fimDaAnterior(anterior.getInicio(), e.inicio()));
        var antes = anterior.snapshot();
        ContratoFiscal novo = novoFiscal(c, e.usuarioId(), anterior.getTipoFiscal(), e.inicio(), anterior.getFim());
        anterior.setFim(fimAnterior);
        anterior.setMotivoEncerramento("Substituição: " + e.motivo().trim());
        anterior.setSubstituidoPorId(novo.getId());
        fiscais.saveAndFlush(anterior);
        historico.alteracao(anterior, antes, e.motivo().trim());
        historico.acao(c.tipoEntidade(), c.getId(), "FISCAL_SUBSTITUICAO", anterior.getNomeExibicao() + " -> "
                + novo.getNomeExibicao() + " (" + novo.getTipoFiscal() + ") a partir de " + novo.getInicio(), e.motivo().trim());
        c.getFiscais().add(novo);
        outbox.registrar("configuracao.fiscal.substituido", Map.of("contratoId", c.getId().toString(),
                "fiscalAnteriorId", anterior.getId().toString(), "fiscalNovoId", novo.getId().toString()));
        return ContratoDetalhe.de(c, hoje());
    }

    /** Encerra uma designação; RN06 impede deixar o contrato sem fiscal vigente. */
    public ContratoDetalhe encerrarFiscal(UUID contratoId, UUID fiscalId, EncerramentoFiscal e) {
        exigirGestao();
        Contrato c = contrato(contratoId);
        ContratoFiscal f = fiscal(c, fiscalId);
        if (e.fim().isBefore(f.getInicio())) {
            throw erroCampo("fim", "O fim da designação não pode ser anterior ao início.");
        }
        LocalDate referencia = e.fim().plusDays(1).isAfter(hoje()) ? e.fim().plusDays(1) : hoje();
        boolean restaOutro = c.getFiscais().stream().filter(x -> !x.getId().equals(fiscalId))
                .anyMatch(x -> x.vigenteEm(referencia));
        if (c.ativo() && !restaOutro && c.utilizavelEm(referencia)) {
            throw new NegocioException("contrato-sem-fiscal", "Contrato ficaria sem fiscal",
                    "Cada contrato deve ter ao menos um fiscal com designação vigente. Use a substituição.",
                    List.of(new br.com.subti.next.configuracao.domain.excecao.ErroCampo("fim",
                            "Designe outro fiscal antes de encerrar esta designação.")));
        }
        var antes = f.snapshot();
        f.setFim(e.fim());
        f.setMotivoEncerramento(e.motivo().trim());
        fiscais.saveAndFlush(f);
        historico.alteracao(f, antes, e.motivo().trim());
        historico.acao(c.tipoEntidade(), c.getId(), "FISCAL_ENCERRAMENTO",
                f.getNomeExibicao() + " até " + e.fim(), e.motivo().trim());
        return ContratoDetalhe.de(c, hoje());
    }

    private ContratoFiscal novoFiscal(Contrato c, String usuarioId, ContratoFiscal.TipoFiscal tipo, LocalDate inicio,
                                      LocalDate fim) {
        var usuarioCore = core.buscar(usuarioId).orElseThrow(() -> erroCampo("usuarioId", "Usuário não encontrado no Core."));
        regra("inicio", () -> Fiscalizacao.validarPeriodo(inicio, fim));
        ContratoFiscal f = new ContratoFiscal();
        f.setContratoId(c.getId());
        f.setUsuarioId(usuarioCore.id());
        f.setNomeExibicao(usuarioCore.nomeExibicao());
        f.setTipoFiscal(tipo);
        f.setInicio(inicio);
        f.setFim(fim);
        fiscais.saveAndFlush(f);
        historico.acao(c.tipoEntidade(), c.getId(), "FISCAL_DESIGNACAO",
                f.getNomeExibicao() + " como " + tipo + " de " + inicio + (fim == null ? "" : " até " + fim), null);
        return f;
    }

    private ContratoFiscal fiscal(Contrato c, UUID fiscalId) {
        return c.getFiscais().stream().filter(x -> x.getId().equals(fiscalId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Designação de fiscal", fiscalId));
    }

    // ---------------------------------------------------------------- apoio

    Contrato contrato(UUID id) {
        return contratos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Contrato", id));
    }

    Contrato contratoVisivel(UUID id) {
        Contrato c = contrato(id);
        UsuarioAtual u = usuario();
        if (u.restritoAFabrica() && !c.getContratada().getId().equals(ContratadaService.fabricaDoUsuario(u))) {
            throw new RecursoNaoEncontradoException("Contrato", id);
        }
        return c;
    }
}
