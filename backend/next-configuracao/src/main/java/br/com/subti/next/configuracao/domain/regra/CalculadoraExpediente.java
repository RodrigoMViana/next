package br.com.subti.next.configuracao.domain.regra;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conta tempo em horário comercial a partir de um calendário (RN12, RN17).
 *
 * <p>O calendário tem fuso horário, um ou mais intervalos de expediente por dia da semana
 * e feriados (datas fixas ou recorrentes todo ano). Noites, fins de semana e feriados
 * não contam.</p>
 */
public final class CalculadoraExpediente {

    /** Limite de segurança para cálculo de prazos (evita laço infinito em calendário sem expediente). */
    private static final int MAX_DIAS = 3660;

    /** Intervalo de expediente em um dia (início inclusivo, fim exclusivo). */
    public record Intervalo(LocalTime inicio, LocalTime fim) {
        public Intervalo {
            if (inicio == null || fim == null || !fim.isAfter(inicio)) {
                throw new IllegalArgumentException("O fim do intervalo de expediente deve ser posterior ao início.");
            }
        }

        Duration duracao() {
            return Duration.between(inicio, fim);
        }
    }

    /** Feriado do calendário. Recorrente: repete todo ano no mesmo dia e mês. */
    public record Feriado(LocalDate data, boolean recorrenteAnual) {
    }

    private final ZoneId fuso;
    private final Map<DayOfWeek, List<Intervalo>> expediente;
    private final Set<LocalDate> feriadosFixos = new HashSet<>();
    private final Set<MonthDay> feriadosRecorrentes = new HashSet<>();

    public CalculadoraExpediente(ZoneId fuso, Map<DayOfWeek, List<Intervalo>> expediente, Collection<Feriado> feriados) {
        this.fuso = fuso;
        this.expediente = new EnumMap<>(DayOfWeek.class);
        expediente.forEach((dia, intervalos) -> {
            List<Intervalo> ordenados = new ArrayList<>(intervalos);
            ordenados.sort(Comparator.comparing(Intervalo::inicio));
            validarSobreposicao(dia, ordenados);
            this.expediente.put(dia, List.copyOf(ordenados));
        });
        for (Feriado f : feriados) {
            if (f.recorrenteAnual()) {
                feriadosRecorrentes.add(MonthDay.from(f.data()));
            } else {
                feriadosFixos.add(f.data());
            }
        }
    }

    /** Verifica se os intervalos de um dia se sobrepõem (permitido mais de um intervalo, RN17). */
    public static void validarSobreposicao(DayOfWeek dia, List<Intervalo> ordenados) {
        for (int i = 1; i < ordenados.size(); i++) {
            if (ordenados.get(i).inicio().isBefore(ordenados.get(i - 1).fim())) {
                throw new IllegalArgumentException("Intervalos de expediente sobrepostos em " + dia + ".");
            }
        }
    }

    public boolean feriado(LocalDate data) {
        return feriadosFixos.contains(data) || feriadosRecorrentes.contains(MonthDay.from(data));
    }

    public boolean diaUtil(LocalDate data) {
        return !feriado(data) && !expediente.getOrDefault(data.getDayOfWeek(), List.of()).isEmpty();
    }

    private List<Intervalo> intervalos(LocalDate data) {
        return feriado(data) ? List.of() : expediente.getOrDefault(data.getDayOfWeek(), List.of());
    }

    /** Tempo útil (em expediente) entre dois instantes. */
    public Duration tempoUtil(Instant inicio, Instant fim) {
        if (!fim.isAfter(inicio)) {
            return Duration.ZERO;
        }
        ZonedDateTime zi = inicio.atZone(fuso);
        ZonedDateTime zf = fim.atZone(fuso);
        Duration total = Duration.ZERO;
        LocalDate dia = zi.toLocalDate();
        LocalDate ultimo = zf.toLocalDate();
        while (!dia.isAfter(ultimo)) {
            for (Intervalo intervalo : intervalos(dia)) {
                ZonedDateTime a = dia.atTime(intervalo.inicio()).atZone(fuso);
                ZonedDateTime b = dia.atTime(intervalo.fim()).atZone(fuso);
                ZonedDateTime de = a.isAfter(zi) ? a : zi;
                ZonedDateTime ate = b.isBefore(zf) ? b : zf;
                if (ate.isAfter(de)) {
                    total = total.plus(Duration.between(de, ate));
                }
            }
            dia = dia.plusDays(1);
        }
        return total;
    }

    /** Instante em que se completa a duração informada, contando só o expediente. */
    public Instant somarTempoUtil(Instant inicio, Duration duracao) {
        if (duracao.isNegative()) {
            throw new IllegalArgumentException("A duração não pode ser negativa.");
        }
        ZonedDateTime cursor = inicio.atZone(fuso);
        Duration restante = duracao;
        LocalDate dia = cursor.toLocalDate();
        for (int i = 0; i < MAX_DIAS; i++) {
            for (Intervalo intervalo : intervalos(dia)) {
                ZonedDateTime a = dia.atTime(intervalo.inicio()).atZone(fuso);
                ZonedDateTime b = dia.atTime(intervalo.fim()).atZone(fuso);
                if (!b.isAfter(cursor)) {
                    continue;
                }
                ZonedDateTime de = a.isAfter(cursor) ? a : cursor;
                if (restante.isZero()) {
                    return de.toInstant();
                }
                Duration disponivel = Duration.between(de, b);
                if (disponivel.compareTo(restante) >= 0) {
                    return de.plus(restante).toInstant();
                }
                restante = restante.minus(disponivel);
            }
            dia = dia.plusDays(1);
        }
        throw new IllegalStateException("O calendário não tem expediente suficiente para calcular o prazo.");
    }

    /**
     * Prazo em dias úteis: fim do expediente do N-ésimo dia útil seguinte ao início
     * (o dia do início conta se o registro ocorreu antes do fim do expediente).
     */
    public Instant somarDiasUteis(Instant inicio, int dias) {
        if (dias <= 0) {
            throw new IllegalArgumentException("Informe ao menos um dia útil.");
        }
        ZonedDateTime zi = inicio.atZone(fuso);
        LocalDate dia = zi.toLocalDate();
        int contados = 0;
        for (int i = 0; i < MAX_DIAS; i++) {
            List<Intervalo> lista = intervalos(dia);
            if (!lista.isEmpty()) {
                ZonedDateTime fimDia = dia.atTime(lista.getLast().fim()).atZone(fuso);
                if (fimDia.isAfter(zi)) {
                    contados++;
                    if (contados == dias) {
                        return fimDia.toInstant();
                    }
                }
            }
            dia = dia.plusDays(1);
        }
        throw new IllegalStateException("O calendário não tem dias úteis suficientes para calcular o prazo.");
    }

    /** Duração total de expediente em uma data (zero em feriados e dias sem expediente). */
    public Duration expedienteDoDia(LocalDate data) {
        return intervalos(data).stream().map(Intervalo::duracao).reduce(Duration.ZERO, Duration::plus);
    }

    public ZoneId fuso() {
        return fuso;
    }
}
