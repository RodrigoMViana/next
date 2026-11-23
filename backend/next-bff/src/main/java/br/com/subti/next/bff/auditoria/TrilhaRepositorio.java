package br.com.subti.next.bff.auditoria;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Trilha de acesso (append-only) no banco next_audit. */
@Repository
public class TrilhaRepositorio {

    public record Registro(String id, Instant ocorridoEm, String usuarioId, String usuarioNome, String perfis,
                           String metodo, String rota, String modulo, String recurso, String acao, int status,
                           String ip, String correlationId, boolean dadoPessoal, Long duracaoMs) {
    }

    public record Filtro(String usuarioId, String modulo, String recurso, String acao, Instant de, Instant ate,
                         Boolean dadoPessoal) {
    }

    private final JdbcTemplate jdbc;

    public TrilhaRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void inserir(Registro r) {
        jdbc.update("INSERT INTO trilha_acesso (id, ocorrido_em, usuario_id, usuario_nome, perfis, metodo, rota, modulo, "
                        + "recurso, acao, status_http, ip, correlation_id, dado_pessoal, duracao_ms) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), Timestamp.from(r.ocorridoEm()), r.usuarioId(), r.usuarioNome(), r.perfis(),
                r.metodo(), r.rota(), r.modulo(), r.recurso(), r.acao(), r.status(), r.ip(), r.correlationId(),
                r.dadoPessoal() ? 1 : 0, r.duracaoMs());
    }

    public long contar(Filtro f) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM trilha_acesso" + where(f, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Registro> pesquisar(Filtro f, int pagina, int tamanho) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT * FROM trilha_acesso" + where(f, args)
                + " ORDER BY ocorrido_em DESC, id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add((long) pagina * tamanho);
        args.add(tamanho);
        return jdbc.query(sql, (rs, i) -> new Registro(rs.getString("id"), rs.getTimestamp("ocorrido_em").toInstant(),
                rs.getString("usuario_id"), rs.getString("usuario_nome"), rs.getString("perfis"), rs.getString("metodo"),
                rs.getString("rota"), rs.getString("modulo"), rs.getString("recurso"), rs.getString("acao"),
                rs.getInt("status_http"), rs.getString("ip"), rs.getString("correlation_id"), rs.getInt("dado_pessoal") == 1,
                rs.getObject("duracao_ms") == null ? null : rs.getLong("duracao_ms")), args.toArray());
    }

    private static String where(Filtro f, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        Map<String, String> iguais = new java.util.LinkedHashMap<>();
        iguais.put("usuario_id", f.usuarioId());
        iguais.put("modulo", f.modulo());
        iguais.put("recurso", f.recurso());
        iguais.put("acao", f.acao());
        iguais.forEach((coluna, valor) -> {
            if (valor != null && !valor.isBlank()) {
                w.append(" AND ").append(coluna).append(" = ?");
                args.add(valor);
            }
        });
        if (f.de() != null) {
            w.append(" AND ocorrido_em >= ?");
            args.add(Timestamp.from(f.de()));
        }
        if (f.ate() != null) {
            w.append(" AND ocorrido_em < ?");
            args.add(Timestamp.from(f.ate()));
        }
        if (f.dadoPessoal() != null) {
            w.append(" AND dado_pessoal = ?");
            args.add(f.dadoPessoal() ? 1 : 0);
        }
        return w.toString();
    }
}
