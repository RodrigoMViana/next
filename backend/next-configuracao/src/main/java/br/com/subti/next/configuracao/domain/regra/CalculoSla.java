package br.com.subti.next.configuracao.domain.regra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cálculo de prazo e situação de uma regra de SLA (RN11 a RN13, RN22).
 *
 * <p>A Configuração parametriza; a Sustentação aplica estas mesmas regras aos chamados.
 * O cálculo é exposto aqui para simulação e para testes de aceite das regras.</p>
 */
public final class CalculoSla {

    /** Forma de contagem do tempo (RN12). */
    public enum FormaContagem { CORRIDO_24X7, HORARIO_COMERCIAL }

    /** Unidade da meta. PERCENTUAL é usado em indicadores sem prazo (por exemplo, disponibilidade). */
    public enum UnidadeMeta { MINUTOS, HORAS, DIAS_UTEIS, DIAS_CORRIDOS, PERCENTUAL }

    /** Situação de um prazo em relação à meta. */
    public enum Situacao { EM_DIA, EM_RISCO, VIOLADO }

    /** Pausa justificada (RN13): o intervalo é descontado do tempo consumido. */
    public record Pausa(Instant inicio, Instant fim, String motivo) {
        public Pausa {
            if (motivo == null || motivo.isBlank()) {
                throw new IllegalArgumentException("Pausa de SLA exige motivo.");
            }
            if (fim != null && fim.isBefore(inicio)) {
                throw new IllegalArgumentException("O fim da pausa não pode ser anterior ao início.");
            }
        }
    }

    /** Resultado da avaliação. */
    public record Avaliacao(Instant prazo, Duration consumido, Duration meta, int percentualConsumido,
                            Situacao situacao) {
    }

    private CalculoSla() {
    }

    /** Valida a combinação forma de contagem × unidade × calendário (RN13). */
    public static void validarRegra(FormaContagem forma, UnidadeMeta unidade, BigDecimal meta, boolean temCalendario) {
        if (meta == null || meta.signum() <= 0) {
            throw new IllegalArgumentException("A meta deve ser maior que zero.");
        }
        if (unidade == UnidadeMeta.PERCENTUAL) {
            if (meta.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Meta percentual deve estar entre 0 e 100.");
            }
            return;
        }
        if (forma == FormaContagem.HORARIO_COMERCIAL && !temCalendario) {
            throw new IllegalArgumentException("Regra com contagem em horário comercial exige calendário.");
        }
        if (forma == FormaContagem.HORARIO_COMERCIAL && unidade == UnidadeMeta.DIAS_CORRIDOS) {
            throw new IllegalArgumentException("Em horário comercial use dias úteis, horas ou minutos.");
        }
        if (forma == FormaContagem.CORRIDO_24X7 && unidade == UnidadeMeta.DIAS_UTEIS) {
            throw new IllegalArgumentException("Em contagem corrida (24x7) use dias corridos, horas ou minutos.");
        }
    }

    /** Duração da meta para unidades de tempo contínuas. */
    public static Duration duracaoMeta(UnidadeMeta unidade, BigDecimal meta) {
        long minutos = switch (unidade) {
            case MINUTOS -> meta.setScale(0, RoundingMode.HALF_UP).longValueExact();
            case HORAS -> meta.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            case DIAS_CORRIDOS -> meta.multiply(BigDecimal.valueOf(1440)).setScale(0, RoundingMode.HALF_UP).longValueExact();
            default -> throw new IllegalArgumentException("Unidade " + unidade + " não é uma duração contínua.");
        };
        return Duration.ofMinutes(minutos);
    }

    /** Prazo sem pausas. */
    public static Instant prazo(Instant inicio, FormaContagem forma, UnidadeMeta unidade, BigDecimal meta,
                                CalculadoraExpediente calendario) {
        if (unidade == UnidadeMeta.PERCENTUAL) {
            throw new IllegalArgumentException("Indicador percentual não tem prazo.");
        }
        if (forma == FormaContagem.CORRIDO_24X7) {
            return inicio.plus(duracaoMeta(unidade, meta));
        }
        if (unidade == UnidadeMeta.DIAS_UTEIS) {
            return calendario.somarDiasUteis(inicio, meta.setScale(0, RoundingMode.UP).intValueExact());
        }
        return calendario.somarTempoUtil(inicio, duracaoMeta(unidade, meta));
    }

    /** Tempo consumido entre o início e "agora", descontando pausas (RN13). */
    public static Duration consumido(Instant inicio, Instant agora, List<Pausa> pausas, FormaContagem forma,
                                     CalculadoraExpediente calendario) {
        Duration total = medir(inicio, agora, forma, calendario);
        for (Pausa p : pausas) {
            Instant de = p.inicio().isBefore(inicio) ? inicio : p.inicio();
            Instant ate = p.fim() == null || p.fim().isAfter(agora) ? agora : p.fim();
            if (ate.isAfter(de)) {
                total = total.minus(medir(de, ate, forma, calendario));
            }
        }
        return total.isNegative() ? Duration.ZERO : total;
    }

    private static Duration medir(Instant de, Instant ate, FormaContagem forma, CalculadoraExpediente calendario) {
        if (forma == FormaContagem.CORRIDO_24X7) {
            return ate.isAfter(de) ? Duration.between(de, ate) : Duration.ZERO;
        }
        return calendario.tempoUtil(de, ate);
    }

    /**
     * Avalia a situação: VIOLADO quando o consumido atinge a meta; EM_RISCO quando o percentual
     * consumido atinge o limiar configurado na notificação (RN22); EM_DIA nos demais casos.
     */
    public static Avaliacao avaliar(Instant inicio, Instant agora, List<Pausa> pausas, FormaContagem forma,
                                    UnidadeMeta unidade, BigDecimal meta, CalculadoraExpediente calendario,
                                    int limiarPercentual) {
        if (limiarPercentual < 1 || limiarPercentual > 99) {
            throw new IllegalArgumentException("O limiar de alerta deve estar entre 1% e 99%.");
        }
        Instant prazoBase = prazo(inicio, forma, unidade, meta, calendario);
        Duration metaDuracao = medir(inicio, prazoBase, forma, calendario);
        Duration consumido = consumido(inicio, agora, pausas, forma, calendario);
        int percentual = metaDuracao.isZero() ? 100
                : (int) Math.min(999, consumido.toSeconds() * 100 / Math.max(1, metaDuracao.toSeconds()));
        Situacao situacao = consumido.compareTo(metaDuracao) >= 0 ? Situacao.VIOLADO
                : percentual >= limiarPercentual ? Situacao.EM_RISCO : Situacao.EM_DIA;
        Duration restante = metaDuracao.minus(consumido);
        Instant prazoComPausas = restante.isNegative() || restante.isZero() ? agora
                : forma == FormaContagem.CORRIDO_24X7 ? agora.plus(restante)
                : calendario.somarTempoUtil(agora, restante);
        if (agora.isBefore(inicio)) {
            prazoComPausas = prazoBase;
        }
        return new Avaliacao(prazoComPausas, consumido, metaDuracao, percentual, situacao);
    }
}
