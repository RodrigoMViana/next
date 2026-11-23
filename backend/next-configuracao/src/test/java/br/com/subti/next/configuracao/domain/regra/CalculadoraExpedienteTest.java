package br.com.subti.next.configuracao.domain.regra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.subti.next.configuracao.domain.regra.CalculadoraExpediente.Feriado;
import br.com.subti.next.configuracao.domain.regra.CalculadoraExpediente.Intervalo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RN12, RN17: contagem em horário comercial com intervalos, fins de semana e feriados. */
class CalculadoraExpedienteTest {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    /** Segunda a sexta, 8h-12h e 14h-18h; 20/11 (recorrente) e 24/11/2026 (fixo) são feriados. */
    static CalculadoraExpediente comercial() {
        Map<DayOfWeek, List<Intervalo>> m = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY)) {
            m.put(d, List.of(new Intervalo(LocalTime.of(14, 0), LocalTime.of(18, 0)),
                    new Intervalo(LocalTime.of(8, 0), LocalTime.of(12, 0))));
        }
        return new CalculadoraExpediente(SP, m, List.of(new Feriado(LocalDate.of(2025, 11, 20), true),
                new Feriado(LocalDate.of(2026, 11, 24), false)));
    }

    static Instant em(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 11, dia, hora, minuto).atZone(SP).toInstant();
    }

    @Test
    void contaApenasOExpediente() {
        CalculadoraExpediente c = comercial();
        // segunda 23/11 10:00 até segunda 23/11 15:00 = 2h (10-12) + 1h (14-15)
        assertThat(c.tempoUtil(em(23, 10, 0), em(23, 15, 0))).isEqualTo(Duration.ofHours(3));
        // sexta 27/11 17:00 até segunda 30/11 09:00 = 1h + 1h
        assertThat(c.tempoUtil(em(27, 17, 0), em(30, 9, 0))).isEqualTo(Duration.ofHours(2));
        // fim antes do início
        assertThat(c.tempoUtil(em(23, 15, 0), em(23, 10, 0))).isEqualTo(Duration.ZERO);
    }

    @Test
    void ignoraFeriadosFixosERecorrentes() {
        CalculadoraExpediente c = comercial();
        assertThat(c.feriado(LocalDate.of(2026, 11, 20))).isTrue();
        assertThat(c.feriado(LocalDate.of(2026, 11, 24))).isTrue();
        assertThat(c.feriado(LocalDate.of(2027, 11, 24))).isFalse();
        assertThat(c.diaUtil(LocalDate.of(2026, 11, 21))).isFalse();
        assertThat(c.diaUtil(LocalDate.of(2026, 11, 23))).isTrue();
        assertThat(c.expedienteDoDia(LocalDate.of(2026, 11, 23))).isEqualTo(Duration.ofHours(8));
        assertThat(c.expedienteDoDia(LocalDate.of(2026, 11, 24))).isEqualTo(Duration.ZERO);
        // segunda 23/11 17:00 a quarta 25/11 09:00: 1h na segunda, terça feriado, 1h na quarta
        assertThat(c.tempoUtil(em(23, 17, 0), em(25, 9, 0))).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void somaTempoUtilAtravessandoAlmocoNoiteEFeriado() {
        CalculadoraExpediente c = comercial();
        assertThat(c.somarTempoUtil(em(23, 11, 0), Duration.ofHours(2))).isEqualTo(em(23, 15, 0));
        // segunda 17:00 + 4h: 1h segunda, terça feriado, quarta 8-11
        assertThat(c.somarTempoUtil(em(23, 17, 0), Duration.ofHours(4))).isEqualTo(em(25, 11, 0));
        // começa fora do expediente (sábado): primeira hora útil é segunda
        assertThat(c.somarTempoUtil(em(28, 10, 0), Duration.ofMinutes(30))).isEqualTo(em(30, 8, 30));
        // duração zero no meio da noite: próximo início de expediente
        assertThat(c.somarTempoUtil(em(23, 20, 0), Duration.ZERO)).isEqualTo(em(25, 8, 0));
        assertThatThrownBy(() -> c.somarTempoUtil(em(23, 8, 0), Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void somaDiasUteis() {
        CalculadoraExpediente c = comercial();
        // segunda 23/11 10:00 + 1 dia útil = fim do expediente da própria segunda
        assertThat(c.somarDiasUteis(em(23, 10, 0), 1)).isEqualTo(em(23, 18, 0));
        // + 3 dias úteis: segunda, (terça feriado), quarta, quinta
        assertThat(c.somarDiasUteis(em(23, 10, 0), 3)).isEqualTo(em(26, 18, 0));
        // aberto depois do expediente: conta a partir do próximo dia útil
        assertThat(c.somarDiasUteis(em(23, 19, 0), 1)).isEqualTo(em(25, 18, 0));
        assertThatThrownBy(() -> c.somarDiasUteis(em(23, 10, 0), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validaIntervalos() {
        assertThatThrownBy(() -> new Intervalo(LocalTime.of(12, 0), LocalTime.of(8, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        Map<DayOfWeek, List<Intervalo>> m = Map.of(DayOfWeek.MONDAY, List.of(
                new Intervalo(LocalTime.of(8, 0), LocalTime.of(12, 0)), new Intervalo(LocalTime.of(11, 0), LocalTime.of(13, 0))));
        assertThatThrownBy(() -> new CalculadoraExpediente(SP, m, List.of())).hasMessageContaining("sobrepostos");
    }

    @Test
    void calendarioSemExpedienteNaoCalculaPrazo() {
        CalculadoraExpediente vazio = new CalculadoraExpediente(SP, Map.of(), List.of());
        assertThatThrownBy(() -> vazio.somarTempoUtil(em(23, 8, 0), Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> vazio.somarDiasUteis(em(23, 8, 0), 1)).isInstanceOf(IllegalStateException.class);
        assertThat(vazio.fuso()).isEqualTo(SP);
    }
}
