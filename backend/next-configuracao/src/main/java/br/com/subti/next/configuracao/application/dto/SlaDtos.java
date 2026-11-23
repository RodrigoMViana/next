package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.Sla;
import br.com.subti.next.configuracao.domain.model.SlaRegra;
import br.com.subti.next.configuracao.domain.model.SlaVersao;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.regra.CalculoSla;
import br.com.subti.next.configuracao.domain.regra.SlaCicloVida.SituacaoVersao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs do SLA (N03). */
public final class SlaDtos {

    private SlaDtos() {
    }

    public record RegraEntrada(@NotBlank @Size(max = 100) String classe, @NotBlank @Size(max = 200) String indicador,
                               @NotNull @Positive BigDecimal metaValor, @NotNull CalculoSla.UnidadeMeta metaUnidade,
                               @NotNull CalculoSla.FormaContagem formaContagem, UUID calendarioId,
                               boolean permitePausa, @Size(max = 500) String observacao) {
    }

    public record SlaEntrada(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 200) String nome,
            @NotNull Sla.TipoResponsavel tipoResponsavel,
            UUID contratoId,
            @Size(max = 200) String equipeInterna,
            @NotNull List<UUID> servicoIds,
            @NotNull List<UUID> sistemaIds,
            @Valid List<RegraEntrada> regras) {
    }

    public record RegrasEntrada(@NotNull @Valid List<RegraEntrada> regras) {
    }

    public record NovaVersao(@NotBlank @Size(max = 1000) String motivoAlteracao) {
    }

    /** Parecer do N2 gestor da fábrica (RF17). */
    public record Parecer(@NotNull Boolean favoravel, @NotBlank @Size(max = 2000) String texto) {
    }

    /** Decisão de vigência pela gestão de TI (RF17). */
    public record Decisao(@NotNull Boolean aprovar, LocalDate vigenciaInicio, @Size(max = 2000) String observacao) {
    }

    public record SimulacaoEntrada(@NotNull UUID regraId, @NotNull Instant inicio, Instant agora,
                                   @Min(1) @Max(99) Integer limiarPercentual, List<PausaEntrada> pausas) {
    }

    public record PausaEntrada(@NotNull Instant inicio, Instant fim, @NotBlank String motivo) {
    }

    public record SimulacaoSaida(Instant inicio, Instant agora, Instant prazo, long minutosConsumidos,
                                 long minutosMeta, int percentualConsumido, CalculoSla.Situacao situacao) {
    }

    public record Ref(UUID id, String codigo, String nome) {
    }

    public record RegraSaida(UUID id, int ordem, String classe, String indicador, BigDecimal metaValor,
                             CalculoSla.UnidadeMeta metaUnidade, CalculoSla.FormaContagem formaContagem,
                             Ref calendario, boolean permitePausa, String observacao) {
        public static RegraSaida de(SlaRegra r) {
            return new RegraSaida(r.getId(), r.getOrdem(), r.getClasse(), r.getIndicador(), r.getMetaValor(),
                    r.getMetaUnidade(), r.getFormaContagem(),
                    r.getCalendario() == null ? null
                            : new Ref(r.getCalendario().getId(), r.getCalendario().getFusoHorario(), r.getCalendario().getNome()),
                    r.isPermitePausa(), r.getObservacao());
        }
    }

    public record VersaoSaida(UUID id, int numero, SituacaoVersao situacao, LocalDate vigenciaInicio,
                              LocalDate vigenciaFim, String motivoAlteracao, Boolean parecerFavoravel,
                              String parecerTexto, String parecerPorNome, Instant parecerEm,
                              String decisaoObservacao, String decisaoPorNome, Instant decisaoEm,
                              List<RegraSaida> regras, long version) {
        public static VersaoSaida de(SlaVersao v) {
            return new VersaoSaida(v.getId(), v.getNumero(), v.getSituacao(), v.getVigenciaInicio(),
                    v.getVigenciaFim(), v.getMotivoAlteracao(), v.getParecerFavoravel(), v.getParecerTexto(),
                    v.getParecerPorNome(), v.getParecerEm(), v.getDecisaoObservacao(), v.getDecisaoPorNome(),
                    v.getDecisaoEm(), v.getRegras().stream().map(RegraSaida::de).toList(), v.versao());
        }
    }

    public record SlaResumo(UUID id, String codigo, String nome, Sla.TipoResponsavel tipoResponsavel,
                            Ref contrato, String contratadaNome, String equipeInterna, Integer versaoVigente,
                            LocalDate vigenteDesde, SituacaoVersao situacaoUltimaVersao, Situacao situacao) {
    }

    public record SlaDetalhe(UUID id, String codigo, String nome, Sla.TipoResponsavel tipoResponsavel,
                             Ref contrato, UUID contratadaId, String contratadaNome, String equipeInterna,
                             List<Ref> servicos, List<ContratoDtos.SistemaSaida> sistemas,
                             List<VersaoSaida> versoes, Situacao situacao, String justificativaSituacao,
                             long version, Instant createdAt, Instant updatedAt) {
    }

    /** Versão vigente em uma data: consumida pela Sustentação na abertura do chamado (RN14). */
    public record VersaoVigente(UUID slaId, String codigo, LocalDate data, VersaoSaida versao) {
    }
}
