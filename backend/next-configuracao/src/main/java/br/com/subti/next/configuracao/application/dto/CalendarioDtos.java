package br.com.subti.next.configuracao.application.dto;

import br.com.subti.next.configuracao.domain.model.Calendario;
import br.com.subti.next.configuracao.domain.model.CalendarioFeriado;
import br.com.subti.next.configuracao.domain.model.Situacao;
import br.com.subti.next.configuracao.domain.regra.CalculoSla;
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
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs do calendário (N10). */
public final class CalendarioDtos {

    private CalendarioDtos() {
    }

    public record Expediente(@Min(1) @Max(7) int diaSemana, @NotNull LocalTime horaInicio,
                             @NotNull LocalTime horaFim) {
    }

    public record Feriado(UUID id, @NotNull LocalDate data, @NotBlank @Size(max = 200) String descricao,
                          @NotNull CalendarioFeriado.Abrangencia abrangencia, boolean recorrenteAnual) {
    }

    public record CalendarioEntrada(
            @NotBlank @Size(max = 120) String nome,
            @Size(max = 500) String descricao,
            @NotBlank @Size(max = 60) String fusoHorario,
            @NotNull LocalDate vigenciaInicio,
            @NotNull @Valid List<Expediente> expedientes,
            @NotNull @Valid List<Feriado> feriados) {
    }

    public record CalendarioSaida(UUID id, String nome, String descricao, String fusoHorario,
                                  LocalDate vigenciaInicio, List<Expediente> expedientes, List<Feriado> feriados,
                                  BigDecimal horasSemanais, Situacao situacao, String justificativaSituacao,
                                  long version, Instant createdAt, Instant updatedAt) {
        public static CalendarioSaida de(Calendario c) {
            long minutos = c.getExpedientes().stream()
                    .mapToLong(e -> java.time.Duration.between(e.getHoraInicio(), e.getHoraFim()).toMinutes()).sum();
            return new CalendarioSaida(c.getId(), c.getNome(), c.getDescricao(), c.getFusoHorario(),
                    c.getVigenciaInicio(),
                    c.getExpedientes().stream().map(e -> new Expediente(e.getDiaSemana(), e.getHoraInicio(),
                            e.getHoraFim())).toList(),
                    c.getFeriados().stream().map(f -> new Feriado(f.getId(), f.getDataFeriado(), f.getDescricao(),
                            f.getAbrangencia(), f.isRecorrenteAnual())).toList(),
                    BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP),
                    c.getSituacao(), c.getJustificativaSituacao(), c.versao(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    /** Simulação de prazo em horário comercial ou corrido. */
    public record SimulacaoEntrada(@NotNull Instant inicio, @NotNull @Positive BigDecimal meta,
                                   @NotNull CalculoSla.UnidadeMeta unidade,
                                   @NotNull CalculoSla.FormaContagem formaContagem) {
    }

    public record SimulacaoSaida(Instant inicio, Instant prazo, long minutosUteis, String fusoHorario) {
    }
}
