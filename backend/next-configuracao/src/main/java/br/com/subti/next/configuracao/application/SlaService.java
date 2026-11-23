package br.com.subti.next.configuracao.application;

import static br.com.subti.next.configuracao.application.Suporte.erroCampo;
import static br.com.subti.next.configuracao.application.Suporte.exigirGestao;
import static br.com.subti.next.configuracao.application.Suporte.exigirLeitura;
import static br.com.subti.next.configuracao.application.Suporte.exigirPerfil;
import static br.com.subti.next.configuracao.application.Suporte.hoje;
import static br.com.subti.next.configuracao.application.Suporte.regra;
import static br.com.subti.next.configuracao.application.Suporte.termo;
import static br.com.subti.next.configuracao.application.Suporte.texto;
import static br.com.subti.next.configuracao.application.Suporte.usuario;
import static br.com.subti.next.configuracao.application.Suporte.verificarVersao;

import br.com.subti.next.configuracao.application.dto.ContratoDtos;
import br.com.subti.next.configuracao.application.dto.SlaDtos.Decisao;
import br.com.subti.next.configuracao.application.dto.SlaDtos.NovaVersao;
import br.com.subti.next.configuracao.application.dto.SlaDtos.Parecer;
import br.com.subti.next.configuracao.application.dto.SlaDtos.Ref;
import br.com.subti.next.configuracao.application.dto.SlaDtos.RegraEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SimulacaoEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SimulacaoSaida;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SlaDetalhe;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SlaEntrada;
import br.com.subti.next.configuracao.application.dto.SlaDtos.SlaResumo;
import br.com.subti.next.configuracao.application.dto.SlaDtos.VersaoSaida;
import br.com.subti.next.configuracao.application.dto.SlaDtos.VersaoVigente;
import br.com.subti.next.configuracao.domain.excecao.AcessoNegadoException;
import br.com.subti.next.configuracao.domain.excecao.ConflitoException;
import br.com.subti.next.configuracao.domain.excecao.RecursoNaoEncontradoException;
import br.com.subti.next.configuracao.domain.model.Calendario;
import br.com.subti.next.configuracao.domain.model.Contrato;
import br.com.subti.next.configuracao.domain.model.Servico;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.Sla;
import br.com.subti.next.configuracao.domain.model.SlaRegra;
import br.com.subti.next.configuracao.domain.model.SlaSistema;
import br.com.subti.next.configuracao.domain.model.SlaVersao;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.porta.PortfolioGateway;
import br.com.subti.next.configuracao.domain.regra.CalculadoraExpediente;
import br.com.subti.next.configuracao.domain.regra.CalculoSla;
import br.com.subti.next.configuracao.domain.regra.SlaCicloVida;
import br.com.subti.next.configuracao.domain.regra.SlaCicloVida.SituacaoVersao;
import br.com.subti.next.configuracao.domain.repositorio.SlaRepository;
import br.com.subti.next.configuracao.domain.repositorio.SlaVersaoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso do SLA parametrizável e versionado (N03, RF09, RF17, RN11 a RN14). */
@Service
@Transactional
public class SlaService {

    private static final Map<String, String> ORDEM = Map.of("codigo", "codigo", "nome", "nome", "situacao", "situacao");

    private final SlaRepository slas;
    private final SlaVersaoRepository versoes;
    private final ContratoService contratoService;
    private final CalendarioService calendarioService;
    private final CadastroApoioService apoio;
    private final PortfolioGateway portfolio;
    private final HistoricoService historico;
    private final OutboxService outbox;

    public SlaService(SlaRepository slas, SlaVersaoRepository versoes, ContratoService contratoService,
                      CalendarioService calendarioService, CadastroApoioService apoio, PortfolioGateway portfolio,
                      HistoricoService historico, OutboxService outbox) {
        this.slas = slas;
        this.versoes = versoes;
        this.contratoService = contratoService;
        this.calendarioService = calendarioService;
        this.apoio = apoio;
        this.portfolio = portfolio;
        this.historico = historico;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Pagina<SlaResumo> listar(String busca, Situacao situacao, Sla.TipoResponsavel tipo, UUID contratoId,
                                    Integer page, Integer size, String sort) {
        exigirLeitura();
        UsuarioAtual u = usuario();
        UUID fabrica = u.restritoAFabrica() ? ContratadaService.fabricaDoUsuario(u) : null;
        return Pagina.de(slas.pesquisar(termo(busca), situacao, tipo, contratoId, fabrica,
                Pagina.pageable(page, size, sort, ORDEM, "codigo")), this::resumo);
    }

    @Transactional(readOnly = true)
    public SlaDetalhe obter(UUID id) {
        exigirLeitura();
        return detalhe(slaVisivel(id));
    }

    /** RF09: SLA para contratada (via contrato vigente, RN01) ou equipe interna, com versão inicial em rascunho. */
    public SlaDetalhe criar(SlaEntrada e) {
        exigirGestao();
        Sla s = new Sla();
        preencher(s, e);
        slas.saveAndFlush(s);
        SlaVersao v = new SlaVersao();
        v.setSlaId(s.getId());
        v.setNumero(1);
        v.setMotivoAlteracao("Versão inicial");
        definirRegras(v, e.regras() == null ? List.of() : e.regras());
        versoes.save(v);
        historico.criacao(s);
        outbox.registrar("configuracao.sla.criado", Map.of("slaId", s.getId().toString(), "codigo", s.getCodigo()));
        return detalhe(s);
    }

    public SlaDetalhe alterar(UUID id, String ifMatch, SlaEntrada e) {
        exigirGestao();
        Sla s = sla(id);
        verificarVersao(s, ifMatch);
        if (e.tipoResponsavel() != s.getTipoResponsavel()) {
            throw erroCampo("tipoResponsavel", "O responsável do SLA não pode ser alterado. Crie outro SLA.");
        }
        var antes = s.snapshot();
        preencher(s, e);
        slas.saveAndFlush(s);
        historico.alteracao(s, antes, null);
        return detalhe(s);
    }

    public SlaDetalhe mudarSituacao(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        Sla s = sla(id);
        boolean temVigente = versaoVigenteEm(s.getId(), hoje()).isPresent();
        if (!ativar && temVigente && texto(justificativa) == null) {
            throw erroCampo("justificativa", "O SLA tem versão vigente. Informe a justificativa da desativação.");
        }
        regra("situacao", () -> {
            if (ativar) {
                s.reativar(texto(justificativa));
            } else {
                s.desativar(texto(justificativa));
            }
        });
        slas.saveAndFlush(s);
        historico.acao(s.tipoEntidade(), s.getId(), ativar ? HistoricoService.REATIVACAO : HistoricoService.DESATIVACAO,
                "Situação: " + s.getSituacao(), texto(justificativa));
        outbox.registrar(ativar ? "configuracao.sla.reativado" : "configuracao.sla.desativado",
                Map.of("slaId", s.getId().toString()));
        return detalhe(s);
    }

    private void preencher(Sla s, SlaEntrada e) {
        String codigo = e.codigo().trim().toUpperCase(java.util.Locale.ROOT);
        if (slas.existsByCodigoIgnoreCaseAndIdNot(codigo, s.getId())) {
            throw new ConflitoException("codigo-duplicado", "Código já cadastrado", "codigo",
                    "Já existe um SLA com o código " + codigo + ".");
        }
        s.setCodigo(codigo);
        s.setNome(e.nome().trim());
        s.setTipoResponsavel(e.tipoResponsavel());
        if (e.tipoResponsavel() == Sla.TipoResponsavel.CONTRATADA) {
            if (e.contratoId() == null) {
                throw erroCampo("contratoId", "Informe o contrato da contratada.");
            }
            Contrato c = contratoService.contrato(e.contratoId());
            boolean mesmoContrato = s.getContrato() != null && s.getContrato().getId().equals(c.getId());
            if (!mesmoContrato && !c.utilizavelEm(hoje())) {
                throw erroCampo("contratoId", "O contrato " + c.getNumero()
                        + " não está ativo ou está fora da vigência efetiva (RN01).");
            }
            s.setContrato(c);
            s.setContratadaId(c.getContratada().getId());
            s.setEquipeInterna(null);
        } else {
            if (texto(e.equipeInterna()) == null) {
                throw erroCampo("equipeInterna", "Informe a equipe interna responsável.");
            }
            s.setContrato(null);
            s.setContratadaId(null);
            s.setEquipeInterna(e.equipeInterna().trim());
        }
        Set<Servico> servicos = new LinkedHashSet<>();
        for (UUID sid : e.servicoIds()) {
            Servico sv = apoio.servico(sid);
            if (!sv.ativo() && !s.getServicos().contains(sv)) {
                throw erroCampo("servicoIds", "O serviço " + sv.getCodigo() + " está desativado.");
            }
            servicos.add(sv);
        }
        if (servicos.isEmpty()) {
            throw erroCampo("servicoIds", "Informe ao menos um serviço monitorado pelo SLA.");
        }
        if (s.getContrato() != null) {
            Set<UUID> doContrato = new HashSet<>();
            s.getContrato().getItens().forEach(i -> doContrato.add(i.getServico().getId()));
            servicos.stream().filter(sv -> !doContrato.contains(sv.getId())).findFirst().ifPresent(sv -> {
                throw erroCampo("servicoIds", "O serviço " + sv.getCodigo() + " não é item do contrato "
                        + s.getContrato().getNumero() + ".");
            });
        }
        s.getServicos().clear();
        s.getServicos().addAll(servicos);
        sincronizarSistemas(s, e.sistemaIds());
    }

    private void sincronizarSistemas(Sla s, List<UUID> ids) {
        Map<UUID, String> atuais = new HashMap<>();
        s.getSistemas().forEach(x -> atuais.put(x.getSistemaId(), x.getSistemaNome()));
        Set<UUID> novos = new LinkedHashSet<>(ids);
        s.getSistemas().removeIf(x -> !novos.contains(x.getSistemaId()));
        for (UUID sid : novos) {
            if (atuais.containsKey(sid)) {
                portfolio.buscar(sid).ifPresent(p -> s.getSistemas().stream()
                        .filter(x -> x.getSistemaId().equals(sid)).forEach(x -> x.setSistemaNome(p.nome())));
                continue;
            }
            String nome = portfolio.buscar(sid).map(PortfolioGateway.SistemaPortfolio::nome)
                    .orElseThrow(() -> erroCampo("sistemaIds", "Sistema " + sid + " não encontrado no Portfólio."));
            s.getSistemas().add(new SlaSistema(sid, nome));
        }
    }

    // ---------------------------------------------------------------- Versões (RN14, RF17)

    /** Substitui as regras de uma versão em rascunho (controle otimista pela versão). */
    public VersaoSaida salvarRegras(UUID slaId, UUID versaoId, String ifMatch, List<RegraEntrada> regras) {
        exigirGestao();
        SlaVersao v = versao(slaId, versaoId);
        verificarVersao(v, ifMatch);
        if (!SlaCicloVida.editavel(v.getSituacao())) {
            throw erroCampo("situacao", "Somente versões em rascunho podem ser editadas. Crie uma nova versão.");
        }
        var antes = v.snapshot();
        definirRegras(v, regras);
        versoes.saveAndFlush(v);
        historico.alteracao(v, antes, null);
        return VersaoSaida.de(v);
    }

    /** RN14: alterar um SLA cria nova versão (cópia das regras da última versão). */
    public VersaoSaida novaVersao(UUID slaId, NovaVersao e) {
        exigirGestao();
        Sla s = sla(slaId);
        List<SlaVersao> lista = versoes.findBySlaIdOrderByNumeroDesc(slaId);
        if (lista.stream().anyMatch(v -> v.getSituacao() == SituacaoVersao.RASCUNHO
                || v.getSituacao() == SituacaoVersao.EM_AVALIACAO)) {
            throw new ConflitoException("versao-aberta", "Já existe versão em elaboração",
                    "Conclua ou devolva a versão em rascunho/avaliação antes de criar outra.");
        }
        SlaVersao base = lista.isEmpty() ? null : lista.getFirst();
        SlaVersao v = new SlaVersao();
        v.setSlaId(slaId);
        v.setNumero(base == null ? 1 : base.getNumero() + 1);
        v.setMotivoAlteracao(e.motivoAlteracao().trim());
        if (base != null) {
            base.getRegras().forEach(r -> v.getRegras().add(r.copia()));
        }
        versoes.save(v);
        historico.acao(s.tipoEntidade(), s.getId(), "SLA_NOVA_VERSAO", "Versão " + v.getNumero() + " criada em rascunho",
                v.getMotivoAlteracao());
        return VersaoSaida.de(v);
    }

    public VersaoSaida enviarParaAvaliacao(UUID slaId, UUID versaoId) {
        exigirGestao();
        Sla s = sla(slaId);
        SlaVersao v = versao(slaId, versaoId);
        regra("situacao", () -> SlaCicloVida.exigirTransicao(v.getSituacao(), SituacaoVersao.EM_AVALIACAO));
        if (v.getRegras().isEmpty()) {
            throw erroCampo("regras", "Cadastre ao menos uma regra antes de enviar para avaliação.");
        }
        v.setSituacao(SituacaoVersao.EM_AVALIACAO);
        v.setParecerFavoravel(null);
        v.setParecerTexto(null);
        v.setParecerPorId(null);
        v.setParecerPorNome(null);
        v.setParecerEm(null);
        versoes.saveAndFlush(v);
        historico.acao(s.tipoEntidade(), s.getId(), "SLA_EM_AVALIACAO", "Versão " + v.getNumero() + " enviada para avaliação", null);
        outbox.registrar("configuracao.sla.em-avaliacao", Map.of("slaId", s.getId().toString(),
                "versao", v.getNumero(), "contratadaId", s.getContratadaId() == null ? "" : s.getContratadaId().toString()));
        return VersaoSaida.de(v);
    }

    /** RF17: o N2 gestor da fábrica registra o parecer, somente do contrato da sua fábrica. */
    public VersaoSaida registrarParecer(UUID slaId, UUID versaoId, Parecer e) {
        exigirPerfil(Suporte.N2_GESTOR);
        Sla s = sla(slaId);
        UsuarioAtual u = usuario();
        if (s.getTipoResponsavel() != Sla.TipoResponsavel.CONTRATADA
                || !s.getContratadaId().equals(u.contratadaId())) {
            throw new AcessoNegadoException("O parecer só pode ser registrado pelo gestor da fábrica do contrato.");
        }
        SlaVersao v = versao(slaId, versaoId);
        if (v.getSituacao() != SituacaoVersao.EM_AVALIACAO) {
            throw erroCampo("situacao", "A versão não está em avaliação.");
        }
        v.setParecerFavoravel(e.favoravel());
        v.setParecerTexto(e.texto().trim());
        v.setParecerPorId(u.id());
        v.setParecerPorNome(u.nome());
        v.setParecerEm(Instant.now());
        versoes.saveAndFlush(v);
        historico.acao(s.tipoEntidade(), s.getId(), "SLA_PARECER", "Versão " + v.getNumero() + ": parecer "
                + (e.favoravel() ? "favorável" : "desfavorável"), e.texto().trim());
        outbox.registrar("configuracao.sla.parecer-registrado", Map.of("slaId", s.getId().toString(),
                "versao", v.getNumero(), "favoravel", e.favoravel()));
        return VersaoSaida.de(v);
    }

    /**
     * RF17: a gestão de TI decide a vigência. Aprovar exige o parecer da contratada (quando o SLA é de
     * contratada) e uma data de início não retroativa; a versão vigente anterior termina na véspera.
     * Devolver retorna a versão para rascunho.
     */
    public VersaoSaida decidir(UUID slaId, UUID versaoId, Decisao e) {
        exigirGestao();
        Sla s = sla(slaId);
        SlaVersao v = versao(slaId, versaoId);
        if (v.getSituacao() != SituacaoVersao.EM_AVALIACAO) {
            throw erroCampo("situacao", "A versão não está em avaliação.");
        }
        UsuarioAtual u = usuario();
        v.setDecisaoObservacao(texto(e.observacao()));
        v.setDecisaoPorId(u.id());
        v.setDecisaoPorNome(u.nome());
        v.setDecisaoEm(Instant.now());
        if (!e.aprovar()) {
            if (texto(e.observacao()) == null) {
                throw erroCampo("observacao", "Informe o motivo da devolução.");
            }
            v.setSituacao(SituacaoVersao.RASCUNHO);
            versoes.saveAndFlush(v);
            historico.acao(s.tipoEntidade(), s.getId(), "SLA_DEVOLVIDO", "Versão " + v.getNumero() + " devolvida para rascunho",
                    e.observacao().trim());
            return VersaoSaida.de(v);
        }
        if (!s.ativo()) {
            throw erroCampo("situacao", "Reative o SLA antes de tornar uma versão vigente.");
        }
        if (s.getTipoResponsavel() == Sla.TipoResponsavel.CONTRATADA && v.getParecerFavoravel() == null) {
            throw erroCampo("parecer", "Aguarde o parecer do gestor da fábrica antes de decidir a vigência.");
        }
        SlaVersao anterior = versoes.findBySlaIdAndSituacao(slaId, SituacaoVersao.VIGENTE).stream()
                .max(Comparator.comparing(SlaVersao::getVigenciaInicio)).orElse(null);
        regra("vigenciaInicio", () -> SlaCicloVida.validarInicioVigencia(e.vigenciaInicio(), hoje(),
                anterior == null ? null : anterior.getVigenciaInicio()));
        if (s.getContrato() != null && !s.getContrato().utilizavelEm(e.vigenciaInicio())) {
            throw erroCampo("vigenciaInicio", "O contrato " + s.getContrato().getNumero()
                    + " não está vigente nessa data (RN01).");
        }
        v.setSituacao(SituacaoVersao.VIGENTE);
        v.setVigenciaInicio(e.vigenciaInicio());
        if (anterior != null) {
            anterior.setVigenciaFim(e.vigenciaInicio().minusDays(1));
            if (!e.vigenciaInicio().isAfter(hoje())) {
                anterior.setSituacao(SituacaoVersao.ENCERRADA);
            }
            versoes.save(anterior);
        }
        versoes.saveAndFlush(v);
        historico.acao(s.tipoEntidade(), s.getId(), "SLA_VIGENCIA", "Versão " + v.getNumero() + " vigente a partir de "
                + v.getVigenciaInicio() + (anterior == null ? "" : "; versão " + anterior.getNumero() + " até "
                + anterior.getVigenciaFim()), texto(e.observacao()));
        outbox.registrar("configuracao.sla.vigente", Map.of("slaId", s.getId().toString(), "versao", v.getNumero(),
                "vigenciaInicio", v.getVigenciaInicio().toString()));
        return VersaoSaida.de(v);
    }

    /** Encerra versões cujo fim de vigência já passou (executado diariamente). */
    public int encerrarVersoesVencidas() {
        int total = 0;
        for (SlaVersao v : versoes.findBySituacao(SituacaoVersao.VIGENTE)) {
            if (v.getVigenciaFim() != null && v.getVigenciaFim().isBefore(hoje())) {
                v.setSituacao(SituacaoVersao.ENCERRADA);
                versoes.save(v);
                historico.acao("sla", v.getSlaId(), "SLA_ENCERRADA", "Versão " + v.getNumero()
                        + " encerrada pelo fim da vigência em " + v.getVigenciaFim(), null);
                total++;
            }
        }
        return total;
    }

    /** RN14: versão vigente na data (consumida pela Sustentação na abertura do chamado). */
    @Transactional(readOnly = true)
    public VersaoVigente vigenteEm(UUID slaId, LocalDate data) {
        exigirLeitura();
        Sla s = slaVisivel(slaId);
        LocalDate d = data == null ? hoje() : data;
        return new VersaoVigente(s.getId(), s.getCodigo(), d,
                versaoVigenteEm(slaId, d).map(VersaoSaida::de).orElse(null));
    }

    Optional<SlaVersao> versaoVigenteEm(UUID slaId, LocalDate data) {
        return versoes.findBySlaIdOrderByNumeroDesc(slaId).stream()
                .filter(v -> v.getSituacao() == SituacaoVersao.VIGENTE || v.getSituacao() == SituacaoVersao.ENCERRADA)
                .filter(v -> v.getVigenciaInicio() != null && !data.isBefore(v.getVigenciaInicio())
                        && (v.getVigenciaFim() == null || !data.isAfter(v.getVigenciaFim())))
                .findFirst();
    }

    /** Simula prazo e situação de uma regra (inclusive pausas, RN13, e limiar de alerta, RN22). */
    @Transactional(readOnly = true)
    public SimulacaoSaida simular(UUID slaId, SimulacaoEntrada e) {
        exigirLeitura();
        slaVisivel(slaId);
        SlaRegra r = versoes.findBySlaIdOrderByNumeroDesc(slaId).stream().flatMap(v -> v.getRegras().stream())
                .filter(x -> x.getId().equals(e.regraId())).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra", e.regraId()));
        if (r.getMetaUnidade() == CalculoSla.UnidadeMeta.PERCENTUAL) {
            throw erroCampo("regraId", "Indicadores percentuais não têm prazo para simular.");
        }
        CalculadoraExpediente calc = r.getCalendario() == null
                ? new CalculadoraExpediente(ZoneId.of("UTC"), Map.of(), List.of()) : r.getCalendario().calculadora();
        Instant agora = e.agora() == null ? Instant.now() : e.agora();
        List<CalculoSla.Pausa> pausas = new ArrayList<>();
        if (e.pausas() != null) {
            if (!e.pausas().isEmpty() && !r.isPermitePausa()) {
                throw erroCampo("pausas", "Esta regra não permite pausa.");
            }
            e.pausas().forEach(p -> pausas.add(regra("pausas", () -> new CalculoSla.Pausa(p.inicio(), p.fim(), p.motivo()))));
        }
        int limiar = e.limiarPercentual() == null ? 80 : e.limiarPercentual();
        CalculoSla.Avaliacao a = regra("regraId", () -> CalculoSla.avaliar(e.inicio(), agora, pausas, r.getFormaContagem(),
                r.getMetaUnidade(), r.getMetaValor(), calc, limiar));
        return new SimulacaoSaida(e.inicio(), agora, a.prazo(), a.consumido().toMinutes(), a.meta().toMinutes(),
                a.percentualConsumido(), a.situacao());
    }

    private void definirRegras(SlaVersao v, List<RegraEntrada> regras) {
        v.getRegras().clear();
        int ordem = 1;
        for (int i = 0; i < regras.size(); i++) {
            RegraEntrada e = regras.get(i);
            String campo = "regras[" + i + "]";
            Calendario cal = e.calendarioId() == null ? null : calendarioService.calendario(e.calendarioId());
            if (cal != null && !cal.ativo()) {
                throw erroCampo(campo + ".calendarioId", "O calendário " + cal.getNome() + " está desativado.");
            }
            regra(campo, () -> CalculoSla.validarRegra(e.formaContagem(), e.metaUnidade(), e.metaValor(), cal != null));
            SlaRegra r = new SlaRegra();
            r.setOrdem(ordem++);
            r.setClasse(e.classe().trim());
            r.setIndicador(e.indicador().trim());
            r.setMetaValor(e.metaValor());
            r.setMetaUnidade(e.metaUnidade());
            r.setFormaContagem(e.formaContagem());
            r.setCalendario(e.formaContagem() == CalculoSla.FormaContagem.HORARIO_COMERCIAL ? cal : null);
            r.setPermitePausa(e.permitePausa());
            r.setObservacao(texto(e.observacao()));
            v.getRegras().add(r);
        }
    }

    // ---------------------------------------------------------------- apoio

    private SlaResumo resumo(Sla s) {
        List<SlaVersao> lista = versoes.findBySlaIdOrderByNumeroDesc(s.getId());
        Optional<SlaVersao> vigente = versaoVigenteEm(s.getId(), hoje());
        return new SlaResumo(s.getId(), s.getCodigo(), s.getNome(), s.getTipoResponsavel(),
                s.getContrato() == null ? null : new Ref(s.getContrato().getId(), s.getContrato().getNumero(), s.getContrato().getObjeto()),
                s.getContrato() == null ? null : s.getContrato().getContratada().getRazaoSocial(), s.getEquipeInterna(),
                vigente.map(SlaVersao::getNumero).orElse(null), vigente.map(SlaVersao::getVigenciaInicio).orElse(null),
                lista.isEmpty() ? null : lista.getFirst().getSituacao(), s.getSituacao());
    }

    private SlaDetalhe detalhe(Sla s) {
        return new SlaDetalhe(s.getId(), s.getCodigo(), s.getNome(), s.getTipoResponsavel(),
                s.getContrato() == null ? null : new Ref(s.getContrato().getId(), s.getContrato().getNumero(), s.getContrato().getObjeto()),
                s.getContratadaId(), s.getContrato() == null ? null : s.getContrato().getContratada().getRazaoSocial(),
                s.getEquipeInterna(),
                s.getServicos().stream().map(x -> new Ref(x.getId(), x.getCodigo(), x.getNome()))
                        .sorted(Comparator.comparing(Ref::codigo)).toList(),
                s.getSistemas().stream().map(x -> new ContratoDtos.SistemaSaida(x.getSistemaId(), x.getSistemaNome())).toList(),
                versoes.findBySlaIdOrderByNumeroDesc(s.getId()).stream().map(VersaoSaida::de).toList(),
                s.getSituacao(), s.getJustificativaSituacao(), s.versao(), s.getCreatedAt(), s.getUpdatedAt());
    }

    Sla sla(UUID id) {
        return slas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("SLA", id));
    }

    private Sla slaVisivel(UUID id) {
        Sla s = sla(id);
        UsuarioAtual u = usuario();
        if (u.restritoAFabrica() && (s.getContratadaId() == null
                || !s.getContratadaId().equals(ContratadaService.fabricaDoUsuario(u)))) {
            throw new RecursoNaoEncontradoException("SLA", id);
        }
        return s;
    }

    private SlaVersao versao(UUID slaId, UUID versaoId) {
        return versoes.findById(versaoId).filter(v -> v.getSlaId().equals(slaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão do SLA", versaoId));
    }
}
