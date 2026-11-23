package br.com.subti.next.configuracao.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs do vínculo com o Portfólio (N04) e do painel. */
public final class PortfolioDtos {

    private PortfolioDtos() {
    }

    public record Vinculo(UUID id, String codigo, String nome) {
    }

    /** Sistema do Portfólio com os vínculos e a sinalização de pendência (RN20). */
    public record SistemaSaida(UUID id, String sigla, String nome, String situacao, String areaResponsavel,
                               List<Vinculo> contratos, List<Vinculo> slas, boolean temSlaVigente,
                               boolean pendenciaConfiguracao) {
    }

    public record Sistemas(boolean portfolioDisponivel, List<SistemaSaida> sistemas) {
    }

    public record VinculoEntrada(@NotNull UUID sistemaId) {
    }

    /** Evento recebido de outro módulo (consumo idempotente). */
    public record EventoEntrada(@NotNull UUID id, @NotNull String type, Integer version, Instant occurredAt,
                                String source, String correlationId, Map<String, Object> payload) {
    }

    public record EventoResultado(UUID id, boolean processado, String mensagem) {
    }

    public record Pendencia(String tipo, String descricao, String recurso, UUID recursoId) {
    }

    public record Painel(Map<String, Long> totais, List<Pendencia> pendencias, long eventosPendentes,
                         boolean portfolioDisponivel, LocalDate dataReferencia) {
    }
}
