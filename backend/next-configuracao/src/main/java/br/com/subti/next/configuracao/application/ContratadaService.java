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

import br.com.subti.next.configuracao.application.dto.ContratadaDtos.AlteracaoEntrada;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.AlteracaoSaida;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.Anexo;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.ContratadaEntrada;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.ContratadaSaida;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.MembroEntrada;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.MembroSaida;
import br.com.subti.next.configuracao.application.dto.ContratadaDtos.ValidacaoCnpj;
import br.com.subti.next.configuracao.domain.excecao.ConflitoException;
import br.com.subti.next.configuracao.domain.excecao.RecursoNaoEncontradoException;
import br.com.subti.next.configuracao.domain.model.Contratada;
import br.com.subti.next.configuracao.domain.model.ContratadaAlteracao;
import br.com.subti.next.configuracao.domain.model.ContratadaMembro;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.UsuarioAtual;
import br.com.subti.next.configuracao.domain.porta.CoreGateway;
import br.com.subti.next.configuracao.domain.regra.Cnpj;
import br.com.subti.next.configuracao.domain.repositorio.ContratadaAlteracaoRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratadaMembroRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratadaRepository;
import br.com.subti.next.configuracao.domain.repositorio.ContratoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso da contratada, sua equipe e alterações contratuais (N02, RF06 a RF08). */
@Service
@Transactional
public class ContratadaService {

    /** Tipos de anexo aceitos (Arquitetura 8.3: lista de tipos permitidos e tamanho máximo). */
    public static final Set<String> TIPOS_ANEXO = Set.of("application/pdf", "image/png", "image/jpeg");
    public static final long TAMANHO_MAXIMO_ANEXO = 5L * 1024 * 1024;

    private static final Map<String, String> ORDEM = Map.of("razaoSocial", "razaoSocial", "cnpj", "cnpj",
            "situacao", "situacao");

    private final ContratadaRepository contratadas;
    private final ContratadaMembroRepository membros;
    private final ContratadaAlteracaoRepository alteracoes;
    private final ContratoRepository contratos;
    private final CoreGateway core;
    private final HistoricoService historico;
    private final OutboxService outbox;

    public ContratadaService(ContratadaRepository contratadas, ContratadaMembroRepository membros,
                             ContratadaAlteracaoRepository alteracoes, ContratoRepository contratos, CoreGateway core,
                             HistoricoService historico, OutboxService outbox) {
        this.contratadas = contratadas;
        this.membros = membros;
        this.alteracoes = alteracoes;
        this.contratos = contratos;
        this.core = core;
        this.historico = historico;
        this.outbox = outbox;
    }

    /** RF06: validação do CNPJ numérico e alfanumérico com mensagem por campo. */
    public ValidacaoCnpj validarCnpj(String entrada) {
        String normalizado = Cnpj.normalizar(entrada);
        var erro = Cnpj.validar(entrada);
        return new ValidacaoCnpj(entrada, normalizado, erro.isEmpty() ? Cnpj.formatar(normalizado) : null,
                erro.isEmpty(), erro.isEmpty() && !normalizado.chars().allMatch(Character::isDigit), erro.orElse(null));
    }

    @Transactional(readOnly = true)
    public Pagina<ContratadaSaida> listar(String busca, Situacao situacao, Integer page, Integer size, String sort) {
        exigirLeitura();
        UsuarioAtual u = usuario();
        UUID fabrica = u.restritoAFabrica() ? fabricaDoUsuario(u) : null;
        String cnpj = busca == null ? null : "%" + Cnpj.normalizar(busca) + "%";
        return Pagina.de(contratadas.pesquisar(termo(busca), cnpj, situacao, fabrica,
                        Pagina.pageable(page, size, sort, ORDEM, "razaoSocial")),
                c -> ContratadaSaida.de(c, contratos.countByContratadaIdAndSituacao(c.getId(), Situacao.ATIVO)));
    }

    @Transactional(readOnly = true)
    public ContratadaSaida obter(UUID id) {
        exigirLeitura();
        Contratada c = contratadaVisivel(id);
        return ContratadaSaida.de(c, contratos.countByContratadaIdAndSituacao(id, Situacao.ATIVO));
    }

    public ContratadaSaida criar(ContratadaEntrada e) {
        exigirGestao();
        Contratada c = new Contratada();
        preencher(c, e);
        contratadas.save(c);
        historico.criacao(c);
        outbox.registrar("configuracao.contratada.criada", Map.of("contratadaId", c.getId().toString()));
        return ContratadaSaida.de(c, 0);
    }

    public ContratadaSaida alterar(UUID id, String ifMatch, ContratadaEntrada e) {
        exigirGestao();
        Contratada c = contratada(id);
        verificarVersao(c, ifMatch);
        var antes = c.snapshot();
        preencher(c, e);
        contratadas.saveAndFlush(c);
        historico.alteracao(c, antes, null);
        return ContratadaSaida.de(c, contratos.countByContratadaIdAndSituacao(id, Situacao.ATIVO));
    }

    /** RN08: contratada com contrato vigente só é desativada com justificativa. */
    public ContratadaSaida mudarSituacao(UUID id, boolean ativar, String justificativa) {
        exigirGestao();
        Contratada c = contratada(id);
        long vigentes = contratos.findByContratadaIdAndSituacao(id, Situacao.ATIVO).stream()
                .filter(k -> k.utilizavelEm(hoje())).count();
        if (!ativar && vigentes > 0 && texto(justificativa) == null) {
            throw erroCampo("justificativa", "A contratada tem " + vigentes
                    + " contrato(s) vigente(s). Informe a justificativa da desativação.");
        }
        regra("situacao", () -> {
            if (ativar) {
                c.reativar(texto(justificativa));
            } else {
                c.desativar(texto(justificativa));
            }
        });
        contratadas.saveAndFlush(c);
        historico.acao(c.tipoEntidade(), c.getId(), ativar ? HistoricoService.REATIVACAO : HistoricoService.DESATIVACAO,
                "Situação: " + c.getSituacao(), texto(justificativa));
        outbox.registrar(ativar ? "configuracao.contratada.reativada" : "configuracao.contratada.desativada",
                Map.of("contratadaId", c.getId().toString()));
        return ContratadaSaida.de(c, contratos.countByContratadaIdAndSituacao(id, Situacao.ATIVO));
    }

    private void preencher(Contratada c, ContratadaEntrada e) {
        String cnpj = Cnpj.normalizar(e.cnpj());
        Cnpj.validar(cnpj).ifPresent(msg -> {
            throw erroCampo("cnpj", msg);
        });
        if (contratadas.existsByCnpjAndIdNot(cnpj, c.getId())) {
            throw new ConflitoException("cnpj-duplicado", "CNPJ já cadastrado", "cnpj",
                    "Já existe uma contratada com o CNPJ " + Cnpj.formatar(cnpj) + ".");
        }
        c.setCnpj(cnpj);
        c.setRazaoSocial(e.razaoSocial().trim());
        c.setNomeFantasia(texto(e.nomeFantasia()));
    }

    // ---------------------------------------------------------------- Equipe (RF07)

    @Transactional(readOnly = true)
    public List<MembroSaida> listarEquipe(UUID contratadaId) {
        exigirLeitura();
        contratadaVisivel(contratadaId);
        LocalDate hoje = hoje();
        return membros.findByContratadaIdOrderByInicioDesc(contratadaId).stream().map(m -> MembroSaida.de(m, hoje)).toList();
    }

    /** RN09: membros são usuários do Core com papel N2 gestor ou N2+ técnico; MFA obrigatório. */
    public MembroSaida adicionarMembro(UUID contratadaId, MembroEntrada e) {
        exigirGestao();
        Contratada c = contratada(contratadaId);
        if (!c.ativo()) {
            throw erroCampo("contratadaId", "A contratada está desativada.");
        }
        var usuarioCore = core.buscar(e.usuarioId())
                .orElseThrow(() -> erroCampo("usuarioId", "Usuário não encontrado no Core."));
        regra("fim", () -> {
            if (e.fim() != null && e.fim().isBefore(e.inicio())) {
                throw new IllegalArgumentException("O fim do período não pode ser anterior ao início.");
            }
        });
        boolean sobreposto = membros.findByUsuarioId(e.usuarioId()).stream()
                .anyMatch(m -> periodosSobrepostos(m.getInicio(), m.getFim(), e.inicio(), e.fim()));
        if (sobreposto) {
            throw new ConflitoException("membro-sobreposto", "Usuário já vinculado", "usuarioId",
                    "O usuário já faz parte de uma equipe de contratada nesse período.");
        }
        ContratadaMembro m = new ContratadaMembro();
        m.setContratadaId(contratadaId);
        m.setUsuarioId(usuarioCore.id());
        m.setNomeExibicao(usuarioCore.nomeExibicao());
        m.setPapel(e.papel());
        m.setInicio(e.inicio());
        m.setFim(e.fim());
        membros.save(m);
        historico.acao("contratada", contratadaId, "EQUIPE_INCLUSAO",
                m.getNomeExibicao() + " como " + m.getPapel() + " a partir de " + m.getInicio(), null);
        outbox.registrar("configuracao.equipe-contratada.alterada", Map.of("contratadaId", contratadaId.toString(),
                "usuarioId", m.getUsuarioId(), "papel", m.getPapel().name(), "mfaObrigatorio", true));
        return MembroSaida.de(m, hoje());
    }

    public MembroSaida encerrarMembro(UUID contratadaId, UUID membroId, LocalDate fim) {
        exigirGestao();
        ContratadaMembro m = membros.findById(membroId).filter(x -> x.getContratadaId().equals(contratadaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro da equipe", membroId));
        if (fim.isBefore(m.getInicio())) {
            throw erroCampo("fim", "O fim do período não pode ser anterior ao início (" + m.getInicio() + ").");
        }
        var antes = m.snapshot();
        m.setFim(fim);
        membros.saveAndFlush(m);
        historico.alteracao(m, antes, null);
        historico.acao("contratada", contratadaId, "EQUIPE_ENCERRAMENTO", m.getNomeExibicao() + " até " + fim, null);
        outbox.registrar("configuracao.equipe-contratada.alterada", Map.of("contratadaId", contratadaId.toString(),
                "usuarioId", m.getUsuarioId(), "fim", fim.toString()));
        return MembroSaida.de(m, hoje());
    }

    private static boolean periodosSobrepostos(LocalDate i1, LocalDate f1, LocalDate i2, LocalDate f2) {
        LocalDate fim1 = f1 == null ? LocalDate.MAX : f1;
        LocalDate fim2 = f2 == null ? LocalDate.MAX : f2;
        return !i1.isAfter(fim2) && !i2.isAfter(fim1);
    }

    // ---------------------------------------------------------------- Alterações contratuais (RF08)

    @Transactional(readOnly = true)
    public List<AlteracaoSaida> listarAlteracoes(UUID contratadaId) {
        exigirLeitura();
        contratadaVisivel(contratadaId);
        return alteracoes.findByContratadaIdOrderByDataAlteracaoDescCreatedAtDesc(contratadaId).stream()
                .map(AlteracaoSaida::de).toList();
    }

    public AlteracaoSaida registrarAlteracao(UUID contratadaId, AlteracaoEntrada e, Anexo anexo) {
        exigirGestao();
        contratada(contratadaId);
        if (e.dataAlteracao().isAfter(hoje())) {
            throw erroCampo("dataAlteracao", "A data da alteração não pode ser futura.");
        }
        ContratadaAlteracao a = new ContratadaAlteracao();
        a.setContratadaId(contratadaId);
        a.setDataAlteracao(e.dataAlteracao());
        a.setTipo(e.tipo().trim());
        a.setDescricao(e.descricao().trim());
        if (anexo != null && anexo.conteudo() != null && anexo.conteudo().length > 0) {
            validarAnexo(anexo);
            a.setAnexoNome(anexo.nome());
            a.setAnexoTipo(anexo.tipo());
            a.setAnexoTamanho((long) anexo.conteudo().length);
            a.setAnexoConteudo(anexo.conteudo());
        }
        alteracoes.save(a);
        historico.acao("contratada", contratadaId, "ALTERACAO_CONTRATUAL",
                a.getTipo() + " em " + a.getDataAlteracao() + (a.getAnexoNome() == null ? "" : " (anexo " + a.getAnexoNome() + ")"),
                a.getDescricao());
        return AlteracaoSaida.de(a);
    }

    @Transactional(readOnly = true)
    public Anexo baixarAnexo(UUID contratadaId, UUID alteracaoId) {
        exigirLeitura();
        contratadaVisivel(contratadaId);
        ContratadaAlteracao a = alteracoes.findById(alteracaoId).filter(x -> x.getContratadaId().equals(contratadaId))
                .filter(x -> x.getAnexoConteudo() != null)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo", alteracaoId));
        return new Anexo(a.getAnexoNome(), a.getAnexoTipo(), a.getAnexoConteudo());
    }

    /** Verifica tipo declarado, tamanho e assinatura do conteúdo (Arquitetura 8.3). */
    static void validarAnexo(Anexo anexo) {
        if (!TIPOS_ANEXO.contains(anexo.tipo())) {
            throw erroCampo("anexo", "Tipo de arquivo não permitido. Envie PDF, PNG ou JPEG.");
        }
        if (anexo.conteudo().length > TAMANHO_MAXIMO_ANEXO) {
            throw erroCampo("anexo", "O anexo deve ter no máximo 5 MB.");
        }
        byte[] b = anexo.conteudo();
        boolean assinaturaOk = switch (anexo.tipo()) {
            case "application/pdf" -> b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
            case "image/png" -> b.length > 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            default -> false;
        };
        if (!assinaturaOk) {
            throw erroCampo("anexo", "O conteúdo do arquivo não corresponde ao tipo informado.");
        }
    }

    // ---------------------------------------------------------------- apoio

    Contratada contratada(UUID id) {
        return contratadas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Contratada", id));
    }

    /** RNF02: N2 gestor só enxerga a própria fábrica (resposta 404 para não revelar a existência). */
    Contratada contratadaVisivel(UUID id) {
        UsuarioAtual u = usuario();
        if (u.restritoAFabrica() && !id.equals(fabricaDoUsuario(u))) {
            throw new RecursoNaoEncontradoException("Contratada", id);
        }
        return contratada(id);
    }

    static UUID fabricaDoUsuario(UsuarioAtual u) {
        return u.contratadaId() == null ? new UUID(0, 0) : u.contratadaId();
    }
}
