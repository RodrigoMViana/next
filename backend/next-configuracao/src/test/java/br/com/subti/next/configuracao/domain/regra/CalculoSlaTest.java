package br.com.subti.next.configuracao.domain.regra;

import static br.com.subti.next.configuracao.domain.regra.CalculadoraExpedienteTest.comercial;
import static br.com.subti.next.configuracao.domain.regra.CalculadoraExpedienteTest.em;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.subti.next.configuracao.domain.regra.CalculoSla.FormaContagem;
import br.com.subti.next.configuracao.domain.regra.CalculoSla.Pausa;
import br.com.subti.next.configuracao.domain.regra.CalculoSla.Situacao;
import br.com.subti.next.configuracao.domain.regra.CalculoSla.UnidadeMeta;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RN11 a RN13 e RN22: prazo, consumo com pausas e situação em relação ao limiar. */
class CalculoSlaTest {

    @Test
    void validaCombinacoesDeRegra() {
        CalculoSla.validarRegra(FormaContagem.CORRIDO_24X7, UnidadeMeta.HORAS, BigDecimal.ONE, false);
        CalculoSla.validarRegra(FormaContagem.HORARIO_COMERCIAL, UnidadeMeta.DIAS_UTEIS, BigDecimal.TEN, true);
        CalculoSla.validarRegra(FormaContagem.CORRIDO_24X7, UnidadeMeta.PERCENTUAL, new BigDecimal("99.5"), false);
        assertThatThrownBy(() -> CalculoSla.validarRegra(FormaContagem.HORARIO_COMERCIAL, UnidadeMeta.HORAS, BigDecimal.ONE, false))
                .hasMessageContaining("exige calendário");
        assertThatThrownBy(() -> CalculoSla.validarRegra(FormaContagem.HORARIO_COMERCIAL, UnidadeMeta.DIAS_CORRIDOS, BigDecimal.ONE, true))
                .hasMessageContaining("dias úteis");
        assertThatThrownBy(() -> CalculoSla.validarRegra(FormaContagem.CORRIDO_24X7, UnidadeMeta.DIAS_UTEIS, BigDecimal.ONE, false))
                .hasMessageContaining("dias corridos");
        assertThatThrownBy(() -> CalculoSla.validarRegra(FormaContagem.CORRIDO_24X7, UnidadeMeta.HORAS, BigDecimal.ZERO, false))
                .hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> CalculoSla.validarRegra(FormaContagem.CORRIDO_24X7, UnidadeMeta.PERCENTUAL, new BigDecimal("101"), false))
                .hasMessageContaining("entre 0 e 100");
    }

    @Test
    void calculaPrazos() {
        var cal = comercial();
        assertThat(CalculoSla.prazo(em(23, 10, 0), FormaContagem.CORRIDO_24X7, UnidadeMeta.MINUTOS, new BigDecimal("30"), cal))
                .isEqualTo(em(23, 10, 30));
        assertThat(CalculoSla.prazo(em(23, 10, 0), FormaContagem.CORRIDO_24X7, UnidadeMeta.DIAS_CORRIDOS, BigDecimal.ONE, cal))
                .isEqualTo(em(24, 10, 0));
        assertThat(CalculoSla.prazo(em(23, 10, 0), FormaContagem.HORARIO_COMERCIAL, UnidadeMeta.HORAS, new BigDecimal("4"), cal))
                .isEqualTo(em(23, 16, 0));
        assertThat(CalculoSla.prazo(em(23, 10, 0), FormaContagem.HORARIO_COMERCIAL, UnidadeMeta.DIAS_UTEIS, new BigDecimal("2"), cal))
                .isEqualTo(em(25, 18, 0));
        assertThat(CalculoSla.duracaoMeta(UnidadeMeta.HORAS, new BigDecimal("1.5"))).isEqualTo(Duration.ofMinutes(90));
        assertThatThrownBy(() -> CalculoSla.prazo(em(23, 10, 0), FormaContagem.CORRIDO_24X7, UnidadeMeta.PERCENTUAL, BigDecimal.TEN, cal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoSla.duracaoMeta(UnidadeMeta.DIAS_UTEIS, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descontaPausasDoTempoConsumido() {
        var cal = comercial();
        List<Pausa> pausas = List.of(new Pausa(em(23, 10, 0), em(23, 11, 0), "Aguardando usuário"));
        Duration d = CalculoSla.consumido(em(23, 8, 0), em(23, 12, 0), pausas, FormaContagem.HORARIO_COMERCIAL, cal);
        assertThat(d).isEqualTo(Duration.ofHours(3));
        // pausa em aberto até "agora"
        List<Pausa> aberta = List.of(new Pausa(em(23, 11, 0), null, "Aguardando fornecedor"));
        assertThat(CalculoSla.consumido(em(23, 8, 0), em(23, 12, 0), aberta, FormaContagem.CORRIDO_24X7, cal))
                .isEqualTo(Duration.ofHours(3));
        assertThatThrownBy(() -> new Pausa(em(23, 11, 0), null, " ")).hasMessageContaining("exige motivo");
        assertThatThrownBy(() -> new Pausa(em(23, 11, 0), em(23, 10, 0), "x")).hasMessageContaining("anterior");
    }

    @Test
    void avaliaSituacaoComLimiar() {
        var cal = comercial();
        BigDecimal quatro = new BigDecimal("4");
        var emDia = CalculoSla.avaliar(em(23, 8, 0), em(23, 9, 0), List.of(), FormaContagem.HORARIO_COMERCIAL,
                UnidadeMeta.HORAS, quatro, cal, 80);
        assertThat(emDia.situacao()).isEqualTo(Situacao.EM_DIA);
        assertThat(emDia.percentualConsumido()).isEqualTo(25);
        assertThat(emDia.prazo()).isEqualTo(em(23, 12, 0));

        var risco = CalculoSla.avaliar(em(23, 8, 0), em(23, 11, 30), List.of(), FormaContagem.HORARIO_COMERCIAL,
                UnidadeMeta.HORAS, quatro, cal, 80);
        assertThat(risco.situacao()).isEqualTo(Situacao.EM_RISCO);

        var violado = CalculoSla.avaliar(em(23, 8, 0), em(23, 15, 0), List.of(), FormaContagem.HORARIO_COMERCIAL,
                UnidadeMeta.HORAS, quatro, cal, 80);
        assertThat(violado.situacao()).isEqualTo(Situacao.VIOLADO);
        assertThat(violado.prazo()).isEqualTo(em(23, 15, 0));

        // com 1h de pausa o prazo se desloca
        var pausado = CalculoSla.avaliar(em(23, 8, 0), em(23, 11, 0),
                List.of(new Pausa(em(23, 9, 0), em(23, 10, 0), "Aguardando usuário")), FormaContagem.HORARIO_COMERCIAL,
                UnidadeMeta.HORAS, quatro, cal, 80);
        assertThat(pausado.consumido()).isEqualTo(Duration.ofHours(2));
        assertThat(pausado.prazo()).isEqualTo(em(23, 15, 0));

        // "agora" antes do início: prazo base
        var futuro = CalculoSla.avaliar(em(23, 8, 0), em(23, 7, 0), List.of(), FormaContagem.CORRIDO_24X7,
                UnidadeMeta.HORAS, quatro, cal, 80);
        assertThat(futuro.prazo()).isEqualTo(em(23, 12, 0));

        assertThatThrownBy(() -> CalculoSla.avaliar(em(23, 8, 0), em(23, 9, 0), List.of(), FormaContagem.CORRIDO_24X7,
                UnidadeMeta.HORAS, quatro, cal, 100)).hasMessageContaining("limiar");
    }
}
