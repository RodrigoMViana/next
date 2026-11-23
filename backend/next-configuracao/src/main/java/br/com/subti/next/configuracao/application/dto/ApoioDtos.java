package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.FonteRecurso;
import br.com.subti.next.configuracao.domain.model.Servico;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.model.UnidadeMedida;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** DTOs dos cadastros de apoio: unidade de medida (N07), fonte de recursos (N08) e serviço (N09). */
public final class ApoioDtos {

    private ApoioDtos() {
    }

    /** Pedido de desativação ou reativação (RF05, RN04, RN08, RN15). */
    public record MudancaSituacao(@Size(max = 500) String justificativa) {
    }

    public record UnidadeMedidaEntrada(
            @NotBlank @Size(max = 20) String sigla,
            @NotBlank @Size(max = 120) String nome,
            @Size(max = 500) String descricao) {
    }

    public record UnidadeMedidaSaida(UUID id, String sigla, String nome, String descricao, Situacao situacao,
                                     String justificativaSituacao, long version, Instant createdAt,
                                     Instant updatedAt) {
        public static UnidadeMedidaSaida de(UnidadeMedida u) {
            return new UnidadeMedidaSaida(u.getId(), u.getSigla(), u.getNome(), u.getDescricao(), u.getSituacao(),
                    u.getJustificativaSituacao(), u.versao(), u.getCreatedAt(), u.getUpdatedAt());
        }
    }

    public record FonteRecursoEntrada(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 200) String nome,
            @Size(max = 500) String descricao) {
    }

    public record FonteRecursoSaida(UUID id, String codigo, String nome, String descricao, Situacao situacao,
                                    String justificativaSituacao, long version, Instant createdAt,
                                    Instant updatedAt) {
        public static FonteRecursoSaida de(FonteRecurso f) {
            return new FonteRecursoSaida(f.getId(), f.getCodigo(), f.getNome(), f.getDescricao(), f.getSituacao(),
                    f.getJustificativaSituacao(), f.versao(), f.getCreatedAt(), f.getUpdatedAt());
        }
    }

    public record ServicoEntrada(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 200) String nome,
            @Size(max = 1000) String descricao,
            @NotNull Servico.Tipo tipo,
            @NotNull UUID unidadeMedidaId) {
    }

    public record Referencia(UUID id, String codigo, String nome) {
    }

    public record ServicoSaida(UUID id, String codigo, String nome, String descricao, Servico.Tipo tipo,
                               Referencia unidadeMedida, Situacao situacao, String justificativaSituacao,
                               long version, Instant createdAt, Instant updatedAt) {
        public static ServicoSaida de(Servico s) {
            UnidadeMedida u = s.getUnidadeMedida();
            return new ServicoSaida(s.getId(), s.getCodigo(), s.getNome(), s.getDescricao(), s.getTipo(),
                    new Referencia(u.getId(), u.getSigla(), u.getNome()), s.getSituacao(),
                    s.getJustificativaSituacao(), s.versao(), s.getCreatedAt(), s.getUpdatedAt());
        }
    }
}
