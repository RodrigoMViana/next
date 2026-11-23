package br.com.subti.next.bff.auditoria;

import br.com.subti.next.bff.modulos.ClienteModulo;
import br.com.subti.next.bff.seguranca.UsuarioSessao;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportação assíncrona da auditoria (RF12, RN24): o usuário solicita, acompanha a situação e baixa
 * por link temporário (token aleatório, guardado como hash, válido por 24 h e só para o solicitante).
 */
@Service
public class ExportacaoServico {

    private static final Logger LOG = LoggerFactory.getLogger(ExportacaoServico.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public record Exportacao(String id, String tipo, String filtros, String situacao, Instant criadoEm,
                             Instant concluidoEm, Long totalRegistros, Instant expiraEm, String mensagemErro) {
    }

    public record Arquivo(String nome, byte[] conteudo) {
    }

    private final JdbcTemplate jdbc;
    private final TrilhaRepositorio trilha;
    private final ClienteModulo modulo;
    private final JsonMapper json;
    private final Duration validade;
    private final int maxRegistros;

    public ExportacaoServico(JdbcTemplate jdbc, TrilhaRepositorio trilha, ClienteModulo modulo, JsonMapper json,
                             @Value("${next.exportacao.validade-link:24h}") Duration validade,
                             @Value("${next.exportacao.max-registros:50000}") int maxRegistros) {
        this.jdbc = jdbc;
        this.trilha = trilha;
        this.modulo = modulo;
        this.json = json;
        this.validade = validade;
        this.maxRegistros = maxRegistros;
    }

    public String registrar(UsuarioSessao u, String tipo, Map<String, String> filtros) {
        String id = UUID.randomUUID().toString();
        String texto = filtros.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        jdbc.update("INSERT INTO exportacao (id, solicitante_id, solicitante_nome, tipo, filtros, situacao, criado_em) "
                + "VALUES (?,?,?,?,?,?,?)", id, u.id(), u.nome(), tipo, texto, "PROCESSANDO", Timestamp.from(Instant.now()));
        return id;
    }

    /** Processa em segundo plano; ao final gera o token do link temporário. */
    @Async
    public void processar(String id, UsuarioSessao u, String tipo, Map<String, String> filtros) {
        try {
            byte[] csv = "GOVERNANCA".equals(tipo) ? governanca(u, filtros) : acessos(filtros);
            long linhas = Math.max(0, new String(csv, StandardCharsets.UTF_8).lines().count() - 1);
            jdbc.update("UPDATE exportacao SET situacao='CONCLUIDA', concluido_em=?, total_registros=?, arquivo=?, "
                            + "expira_em=? WHERE id=?", Timestamp.from(Instant.now()), linhas, csv,
                    Timestamp.from(Instant.now().plus(validade)), id);
        } catch (RuntimeException e) {
            LOG.error("falha na exportação {}", id, e);
            jdbc.update("UPDATE exportacao SET situacao='FALHOU', concluido_em=?, mensagem_erro=? WHERE id=?",
                    Timestamp.from(Instant.now()), "Não foi possível gerar o arquivo. Tente novamente.", id);
        }
    }

    private byte[] acessos(Map<String, String> f) {
        TrilhaRepositorio.Filtro filtro = new TrilhaRepositorio.Filtro(f.get("usuarioId"), f.get("modulo"), f.get("recurso"),
                f.get("acao"), instante(f.get("de")), instante(f.get("ate")), null);
        StringBuilder sb = new StringBuilder("﻿ocorrido_em;usuario_id;usuario_nome;perfis;metodo;rota;modulo;recurso;acao;status;ip;correlation_id;dado_pessoal\n");
        int pagina = 0;
        int total = 0;
        while (total < maxRegistros) {
            List<TrilhaRepositorio.Registro> lote = trilha.pesquisar(filtro, pagina++, 500);
            for (var r : lote) {
                sb.append(String.join(";", s(r.ocorridoEm()), s(r.usuarioId()), s(r.usuarioNome()), s(r.perfis()), r.metodo(),
                        s(r.rota()), s(r.modulo()), s(r.recurso()), r.acao(), String.valueOf(r.status()), s(r.ip()),
                        s(r.correlationId()), r.dadoPessoal() ? "S" : "N")).append('\n');
            }
            total += lote.size();
            if (lote.size() < 500) {
                break;
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] governanca(UsuarioSessao u, Map<String, String> f) {
        StringBuilder sb = new StringBuilder("﻿ocorrido_em;usuario_id;usuario_nome;entidade;entidade_id;acao;campo;valor_anterior;valor_novo;justificativa;correlation_id\n");
        int pagina = 0;
        int total = 0;
        String base = f.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        while (total < maxRegistros) {
            String q = (base.isEmpty() ? "" : base + "&") + "size=100&page=" + pagina++;
            var r = modulo.enviar(u, "GET", "/internal/v1/auditoria/governanca", q, null, Map.of("Accept", "application/json"));
            if (r.statusCode() != 200) {
                throw new IllegalStateException("módulo respondeu " + r.statusCode());
            }
            JsonNode raiz = json.readTree(r.body());
            JsonNode conteudo = raiz.get("content");
            for (JsonNode h : conteudo) {
                sb.append(String.join(";", t(h, "ocorridoEm"), t(h, "usuarioId"), t(h, "usuarioNome"), t(h, "entidade"),
                        t(h, "entidadeId"), t(h, "acao"), t(h, "campo"), t(h, "valorAnterior"), t(h, "valorNovo"),
                        t(h, "justificativa"), t(h, "correlationId"))).append('\n');
            }
            total += conteudo.size();
            if (conteudo.size() < 100) {
                break;
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public List<Exportacao> listar(UsuarioSessao u) {
        return jdbc.query("SELECT id, tipo, filtros, situacao, criado_em, concluido_em, total_registros, expira_em, mensagem_erro "
                        + "FROM exportacao WHERE solicitante_id = ? ORDER BY criado_em DESC FETCH FIRST 20 ROWS ONLY",
                (rs, i) -> new Exportacao(rs.getString("id"), rs.getString("tipo"), rs.getString("filtros"),
                        rs.getString("situacao"), instante(rs.getTimestamp("criado_em")), instante(rs.getTimestamp("concluido_em")),
                        rs.getObject("total_registros") == null ? null : rs.getLong("total_registros"),
                        instante(rs.getTimestamp("expira_em")), rs.getString("mensagem_erro")), u.id());
    }

    /** Gera (ou renova) o token do link temporário de uma exportação concluída do próprio usuário. */
    public Optional<String> gerarLink(UsuarioSessao u, String id) {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(24));
        int n = jdbc.update("UPDATE exportacao SET token_hash=? WHERE id=? AND solicitante_id=? AND situacao='CONCLUIDA' "
                + "AND expira_em > ?", hash(token), id, u.id(), Timestamp.from(Instant.now()));
        return n == 1 ? Optional.of(token) : Optional.empty();
    }

    public Optional<Arquivo> baixar(UsuarioSessao u, String id, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        List<Arquivo> r = jdbc.query("SELECT tipo, criado_em, arquivo FROM exportacao WHERE id=? AND solicitante_id=? "
                        + "AND situacao='CONCLUIDA' AND token_hash=? AND expira_em > ?",
                (rs, i) -> new Arquivo("auditoria-" + rs.getString("tipo").toLowerCase() + "-"
                        + rs.getTimestamp("criado_em").toInstant().toString().substring(0, 10) + ".csv", rs.getBytes("arquivo")),
                id, u.id(), hash(token), Timestamp.from(Instant.now()));
        return r.stream().findFirst();
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        ALEATORIO.nextBytes(b);
        return b;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instante(String s) {
        return s == null || s.isBlank() ? null : Instant.parse(s);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static String s(Object o) {
        return o == null ? "" : csv(o.toString());
    }

    private static String t(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? "" : csv(v.asString());
    }

    /** Neutraliza separador, quebras de linha e injeção de fórmula em planilhas. */
    private static String csv(String v) {
        String limpo = v.replace(";", ",").replace("\r", " ").replace("\n", " ");
        return !limpo.isEmpty() && "=+-@".indexOf(limpo.charAt(0)) >= 0 ? "'" + limpo : limpo;
    }

    @SuppressWarnings("unused")
    private static ByteArrayOutputStream vazio() {
        return new ByteArrayOutputStream();
    }
}
